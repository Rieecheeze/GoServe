$ErrorActionPreference = 'Stop'
$goServePlanRoot = Split-Path -Parent $PSScriptRoot
$goServePlanDocx = Join-Path $goServePlanRoot 'output/docx/GoServe_Project_Plan_v2.2.docx'
$goServePlanPdf = Join-Path $goServePlanRoot 'output/pdf/GoServe_Project_Plan_v2.2.pdf'
$goServePlanWord = $null
$goServePlanDocument = $null
try {
    $goServePlanWord = New-Object -ComObject Word.Application
    $goServePlanWord.Visible = $false
    $goServePlanWord.DisplayAlerts = 0
    $goServePlanDocument = $goServePlanWord.Documents.Open($goServePlanDocx, $false, $true)
    $goServePlanDocument.Fields.Update() | Out-Null
    $goServePlanDocument.Repaginate()
    $goServePlanDocument.ExportAsFixedFormat($goServePlanPdf, 17)
    Write-Output "Rendered actual DOCX to $goServePlanPdf"
} finally {
    if ($null -ne $goServePlanDocument) {
        $goServePlanDocument.Close(0)
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($goServePlanDocument) | Out-Null
    }
    if ($null -ne $goServePlanWord) {
        $goServePlanWord.Quit()
        [Runtime.InteropServices.Marshal]::FinalReleaseComObject($goServePlanWord) | Out-Null
    }
}
