package database

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"sort"
	"strings"
)

// RunMigrations aplica las migraciones *.up.sql del directorio migrationsDir
// que AUN no estan registradas en la tabla schema_migrations. Cada archivo
// se ejecuta exactamente una vez: cuando se aplica con exito, su version
// (basada en el nombre del archivo sin extension) se inserta en
// schema_migrations. En arranques subsiguientes se salta el archivo.
//
// Antes de iterar los archivos, se asegura que schema_migrations exista.
// La tabla es controlada por migrate.go (no por las migraciones) para
// evitar una recursion: 0000 necesita insertar en schema_migrations, pero
// schema_migrations no existiria al momento de correr 0000 si dependiera
// de otra migration. Al crearla aca, antes de iterar, queda disponible
// desde la primera migration.
//
// Por que este esquema reemplaza al anterior: la version vieja ejecutaba
// todos los .up.sql en cada arranque, y 0001_schema.up.sql contiene 22
// DROP TABLE IF EXISTS al inicio. Resultado: cada restart borraba todas
// las tablas del usuario. El nuevo esquema aplica cada migration una
// sola vez, asi los datos sobreviven a los restarts.
//
// El archivo 0000_schema_tracking.up.sql es el bootstrap: crea la tabla
// (no-op via IF NOT EXISTS) y registra como aplicadas todas las migrations
// que existian antes de introducir este sistema. Asi, en una BD
// pre-existente, las migrations previas NO se vuelven a correr y los
// datos sobreviven al primer restart.
func RunMigrations(db *sql.DB, migrationsDir string) error {
	// 1. Asegurar que la tabla de control exista antes de iterar archivos.
	if err := ensureMigrationsTable(db); err != nil {
		return fmt.Errorf("creando tabla schema_migrations: %w", err)
	}

	// 2. Leer las versiones ya aplicadas.
	applied, err := getAppliedVersions(db)
	if err != nil {
		return fmt.Errorf("leyendo versiones aplicadas: %w", err)
	}

	// 3. Listar los archivos *.up.sql en orden alfabetico (0000, 0001, ...).
	entries, err := os.ReadDir(migrationsDir)
	if err != nil {
		return fmt.Errorf("leyendo directorio de migraciones %s: %w", migrationsDir, err)
	}
	var files []string
	for _, e := range entries {
		if e.IsDir() {
			continue
		}
		if !strings.HasSuffix(e.Name(), ".up.sql") {
			continue
		}
		files = append(files, e.Name())
	}
	sort.Strings(files)

	// 4. Aplicar solo los archivos no registrados y marcarlos como aplicados.
	for _, name := range files {
		version := strings.TrimSuffix(name, ".up.sql")
		if _, ok := applied[version]; ok {
			continue
		}
		if err := applyMigration(db, migrationsDir, name); err != nil {
			return fmt.Errorf("aplicando %s: %w", name, err)
		}
		// INSERT IGNORE por si dos restarts corren el mismo 0000 a la vez
		// (carrera improbable pero defenderse en profundidad cuesta nada).
		if _, err := db.Exec(
			"INSERT IGNORE INTO schema_migrations (version) VALUES (?)",
			version,
		); err != nil {
			return fmt.Errorf("registrando %s en schema_migrations: %w", name, err)
		}
	}
	return nil
}

// ensureMigrationsTable crea la tabla de control si no existe. Idempotente
// (IF NOT EXISTS). Se ejecuta al inicio de RunMigrations antes que cualquier
// *.up.sql, asi las migraciones pueden insertar en ella inmediatamente (el
// caso principal es 0000 que registra las versiones pre-existentes).
func ensureMigrationsTable(db *sql.DB) error {
	_, err := db.Exec(`
        CREATE TABLE IF NOT EXISTS schema_migrations (
            version    VARCHAR(255) NOT NULL PRIMARY KEY,
            applied_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
    `)
	return err
}

// getAppliedVersions devuelve el set de versiones ya registradas. La clave
// del map es la version (el nombre del archivo sin extension: ej
// "0001_schema"). Devuelve un map vacio si la tabla esta vacia.
func getAppliedVersions(db *sql.DB) (map[string]struct{}, error) {
	rows, err := db.Query("SELECT version FROM schema_migrations")
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	applied := make(map[string]struct{})
	for rows.Next() {
		var v string
		if err := rows.Scan(&v); err != nil {
			return nil, err
		}
		applied[v] = struct{}{}
	}
	return applied, rows.Err()
}

// applyMigration lee el archivo, lo divide respetando directivas DELIMITER
// y ejecuta cada sentencia via db.Exec. La logica de splitting es
// necesaria para los stored procedures con DELIMITER $$ de 0001_schema.up.sql.
func applyMigration(db *sql.DB, migrationsDir, name string) error {
	path := filepath.Join(migrationsDir, name)
	content, err := os.ReadFile(path)
	if err != nil {
		return fmt.Errorf("leyendo migracion %s: %w", name, err)
	}
	return splitAndExec(db, string(content))
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