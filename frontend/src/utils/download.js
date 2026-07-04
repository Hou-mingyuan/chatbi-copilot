export function downloadBlob(resp, fallbackName = 'download.bin') {
  const blob = resp.data
  const disposition = resp.headers?.['content-disposition'] || ''
  let name = fallbackName
  const match = /filename\*?=(?:UTF-8'')?([^;]+)/i.exec(disposition)
  if (match) {
    name = decodeURIComponent(match[1].trim().replace(/"/g, ''))
  }
  const url = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = url
  a.download = name
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  URL.revokeObjectURL(url)
}
