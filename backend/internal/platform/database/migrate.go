package database

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// RunMigrations aplica todos los ficheros *.up.sql del directorio migrationsDir
// en orden alfabetico. Como el schema es idempotente (DROP IF EXISTS +
// CREATE), ejecutar todo en cada arranque es seguro y evita una biblioteca
// de migraciones (golang-migrate fue eliminado por ponytail-audit).
//
// Detalle critico: los ficheros usan `DELIMITER $$` para definir stored
// procedures. Esa directiva NO es SQL del servidor MariaDB/MySQL; es una
// convencion del cliente mysql CLI para cambiar el separador de sentencias.
// database/sql con multiStatements=true no la procesa, por lo que aqui se
// trocea el contenido siguiendo los cambios de DELIMITER antes de ejecutar.
func RunMigrations(db *sql.DB, migrationsDir string) error {
	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("leyendo directorio de migraciones %s: %w", migrationsDir, err)
	}

	var files []string
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		if strings.HasSuffix(e.Name(), ".up.sql") {
			files = append(files, e.Name())
		}
	}
	sort.Strings(files)

	for _, name := range files {
		path := filepath.Join(migrationsDir, name)
		content, err := os.ReadFile(path)
		if err != nil {
			return fmt.Errorf("leyendo migracion %s: %w", path, err)
		}
		if err := splitAndExec(db, string(content)); err != nil {
			return fmt.Errorf("migracion %s: %w", name, err)
		}
	}
	return nil
}

// splitAndExec trocea content siguiendo las directivas DELIMITER y ejecuta
// cada sentencia por separado con db.Exec.
//
// Logica:
//  1. Separador por defecto: ";"
//  2. Linea que empieza con "DELIMITER X" cambia el separador a X (sin
//     ejecutar la propia linea, que no es SQL).
//  3. Se acumulan lineas en un buffer. Cuando la linea termina con el
//     separador actual, el buffer se envia como una sola sentencia a db.Exec.
//  4. Para DELIMITER $$ el bloque completo del CREATE PROCEDURE ... END$$ se
//     envia de un golpe, preservando los ";" internos.
//
// El sufijo se comprueba sobre la linea tras quitar espacios, no sobre el
// buffer acumulado: eso permite que un CREATE PROCEDURE con ";" internos
// solo se corte en el "$$" final.
func splitAndExec(db *sql.DB, content string) error {
	for _, s := range splitStatements(content) {
		if _, err := db.Exec(s); err != nil {
			return fmt.Errorf("ejecutando sentencia: %w\nsentencia: %s", err, s)
		}
	}
	return nil
}

// splitStatements trocea content en sentencias individuales siguiendo las
// directivas DELIMITER, ya recortado el delimitador final (";" o "$$") de
// cada sentencia: MariaDB no entiende el delimitador custom como sintaxis,
// solo el cliente mysql CLI lo interpreta.
func splitStatements(content string) []string {
	lines := strings.Split(content, "\n")
	delimiter := ";"
	var stmt strings.Builder
	var statements []string

	for _, line := range lines {
		trimmed := strings.TrimSpace(line)

		// Directiva DELIMITER: cambia el separador y descarta la linea.
		if strings.HasPrefix(trimmed, "DELIMITER ") {
			delimiter = strings.TrimSpace(strings.TrimPrefix(trimmed, "DELIMITER "))
			continue
		}

		stmt.WriteString(line)
		stmt.WriteString("\n")

		if delimiter == "" {
			continue
		}
		if !strings.HasSuffix(trimmed, delimiter) {
			continue
		}

		s := strings.TrimSpace(stmt.String())
		s = strings.TrimSpace(strings.TrimSuffix(s, delimiter))
		if s != "" {
			statements = append(statements, s)
		}
		stmt.Reset()
	}

	// Resto final sin separador (ficheros sin salto de linea al final).
	if s := strings.TrimSpace(stmt.String()); s != "" {
		statements = append(statements, s)
	}
	return statements
}
