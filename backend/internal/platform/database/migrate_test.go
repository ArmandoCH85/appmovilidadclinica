package database

import "testing"

func TestSplitStatements_DelimiterStripped(t *testing.T) {
	content := "CREATE TABLE t (id INT);\n" +
		"DELIMITER $$\n" +
		"CREATE PROCEDURE p()\n" +
		"BEGIN\n" +
		"    SELECT 1;\n" +
		"END$$\n" +
		"DELIMITER ;\n" +
		"DROP TABLE t;\n"

	got := splitStatements(content)
	if len(got) != 3 {
		t.Fatalf("esperaba 3 sentencias, obtuve %d: %#v", len(got), got)
	}
	if got[0] != "CREATE TABLE t (id INT);" {
		t.Errorf("sentencia 0 = %q", got[0])
	}
	want1 := "CREATE PROCEDURE p()\nBEGIN\n    SELECT 1;\nEND"
	if got[1] != want1 {
		t.Errorf("sentencia 1 = %q, want %q (no debe terminar en $$)", got[1], want1)
	}
	if got[2] != "DROP TABLE t;" {
		t.Errorf("sentencia 2 = %q", got[2])
	}
}
