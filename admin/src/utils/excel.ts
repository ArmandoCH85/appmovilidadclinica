// ============================================================================
// utils/excel.ts
// Helper de exportacion a .xlsx para los reportes del admin.
// Usa SheetJS Community (xlsx) — MIT, sin dependencias nativas.
//
// Convierte una lista de objetos (los mismos `items` que devuelve el backend)
// en una planilla Excel con:
//   - Titulo del reporte en la fila 1
//   - Filtros aplicados en la fila 2 (si los hay)
//   - Headers en espanol en la fila 4
//   - Datos desde la fila 5
//   - Fila opcional de totales al final
//
// Uso:
//   exportRowsToExcel(rows, columns, {
//     filename: 'reporte_actividad_usuarios',
//     sheetName: 'Actividad por Usuario',
//     title: 'Reporte #28: Actividad de reservas por usuario',
//     filterDescription: 'Rol=WORKER, Rango=2026-08-03 a 2026-08-07',
//     totals: { total: 42, bySelf: 35, ... },
//     totalsLabel: 'TOTAL'
//   })
// ============================================================================

import * as XLSX from 'xlsx'

export type CellFormat = 'text' | 'date' | 'datetime' | 'number' | 'percent'

export interface ExcelColumn {
  /** Nombre de la propiedad en el objeto row */
  key: string
  /** Etiqueta que aparecera en el header (en espanol) */
  label: string
  /** Formato opcional para la celda. Default: text. */
  format?: CellFormat
  /** Ancho de columna en caracteres (default: 18) */
  width?: number
}

export interface ExportOptions {
  /** Nombre del archivo SIN extension. Se le agrega .xlsx automaticamente */
  filename: string
  /** Nombre de la hoja (max 31 chars en Excel) */
  sheetName: string
  /** Titulo del reporte en la fila 1 */
  title: string
  /** Descripcion legible de los filtros aplicados (fila 2). Si vacio, se omite. */
  filterDescription?: string
  /** Fila opcional de totales al final. La primera celda usa totalsLabel. */
  totals?: Record<string, unknown>
  totalsLabel?: string
}

/**
 * Exporta un array de filas a un archivo .xlsx y dispara la descarga.
 * El archivo se descarga inmediatamente via Blob + anchor; no requiere
 * intervencion del usuario.
 */
export function exportRowsToExcel(
  rows: Record<string, unknown>[],
  columns: ExcelColumn[],
  options: ExportOptions,
): void {
  const wb = XLSX.utils.book_new()

  // Construir la matriz de celdas manualmente para tener control sobre el
  // header (titulo + filtros) y la fila de totales.
  const aoa: unknown[][] = []

  // Fila 1: titulo del reporte
  aoa.push([options.title])
  // Fila 2: filtros aplicados (solo si hay)
  if (options.filterDescription) {
    aoa.push([`Filtros: ${options.filterDescription}`])
  } else {
    aoa.push([''])
  }
  // Fila 3: separador
  aoa.push([])
  // Fila 4: headers
  aoa.push(columns.map((c) => c.label))
  // Filas 5+: datos
  for (const row of rows) {
    aoa.push(columns.map((c) => normalizeCell(row[c.key], c.format)))
  }
  // Fila final: totales (si los hay)
  if (options.totals) {
    const totals = options.totals
    aoa.push(
      columns.map((c) => {
        if (c.key === '__label__') return options.totalsLabel ?? 'TOTAL'
        const v = totals[c.key]
        return v !== undefined ? v : ''
      }),
    )
  }

  const ws = XLSX.utils.aoa_to_sheet(aoa)

  // Merge del titulo a lo ancho de las columnas (mas estetico)
  const titleRow = options.filterDescription ? 2 : 1
  const lastCol = XLSX.utils.encode_col(columns.length - 1)
  ws[`!merges`] = [
    { s: { r: 0, c: 0 }, e: { r: 0, c: columns.length - 1 } },
    { s: { r: 1, c: 0 }, e: { r: 1, c: columns.length - 1 } },
  ]
  void titleRow

  // Anchos de columna
  ws['!cols'] = columns.map((c) => ({ wch: c.width ?? 18 }))

  // Formatos por columna (z-format strings de Excel)
  for (let i = 0; i < columns.length; i++) {
    const fmt = columns[i].format
    if (!fmt || fmt === 'text') continue
    const col = XLSX.utils.encode_col(i)
    for (let r = 3; r < aoa.length; r++) {
      const addr = `${col}${r + 1}`
      const cell = ws[addr]
      if (!cell) continue
      // Solo aplicar formato si la celda tiene valor (no vacia)
      if (cell.v === '' || cell.v === null || cell.v === undefined) continue
      switch (fmt) {
        case 'date':
          cell.z = 'yyyy-mm-dd'
          break
        case 'datetime':
          cell.z = 'yyyy-mm-dd hh:mm:ss'
          break
        case 'number':
          cell.z = '#,##0'
          break
        case 'percent':
          cell.z = '0.00"%"'
          break
      }
    }
  }

  // Nombre de hoja truncado a 31 chars (limite Excel)
  const sheetName = options.sheetName.slice(0, 31)
  XLSX.utils.book_append_sheet(wb, ws, sheetName)

  // Generar archivo y disparar descarga
  XLSX.writeFile(wb, `${options.filename}.xlsx`)
}

/**
 * Normaliza un valor para que SheetJS lo escriba correctamente.
 * - strings ISO de fecha/datetime → Date object (para que aplique formato)
 * - null/undefined → string vacio (mas amigable que null en la celda)
 * - numeros y booleanos → 그대로
 */
function normalizeCell(
  value: unknown,
  format: CellFormat | undefined,
): unknown {
  if (value === null || value === undefined) return ''
  if (format === 'date' || format === 'datetime') {
    if (value instanceof Date) return value
    if (typeof value === 'string') {
      const d = new Date(value)
      return Number.isNaN(d.getTime()) ? value : d
    }
  }
  return value
}

/**
 * Genera un nombre de archivo estandar con timestamp local.
 * Formato: {prefix}_{YYYY-MM-DD}_{HH-MM}.xlsx
 */
export function timestampedFilename(prefix: string): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  const ts =
    `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}` +
    `_${pad(now.getHours())}-${pad(now.getMinutes())}`
  return `${prefix}_${ts}`
}