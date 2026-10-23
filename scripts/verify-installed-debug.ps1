param([string]$Adb='D:/ANDROID STUDIO FILES/platform-tools/adb.exe',[string]$Serial='emulator-5580')
$ErrorActionPreference='Stop'
$proofRoot=Split-Path -Parent $PSScriptRoot
$proofOutput=Join-Path $proofRoot 'output/validation/final-2026-10-22'
New-Item -ItemType Directory -Force -Path $proofOutput | Out-Null
function Invoke-GoServeProof([string[]]$Arguments){
    $result=& $Adb -s $Serial @Arguments
    if($LASTEXITCODE -ne 0){throw "Emulator operation failed: $($Arguments[0])"}
    return $result
}
if((Invoke-GoServeProof @('emu','avd','name')) -notcontains 'GoServe_API_26'){throw 'The reserved emulator is not the task-owned GoServe AVD.'}
$proofSize=(Invoke-GoServeProof @('shell','wm','size')) -join "`n"
$proofDensity=(Invoke-GoServeProof @('shell','wm','density')) -join "`n"
$proofFont=(Invoke-GoServeProof @('shell','settings','get','system','font_scale')).Trim()
$proofMotion=(Invoke-GoServeProof @('shell','settings','get','global','animator_duration_scale')).Trim()
function Save-GoServeProof([string]$Name){
    Invoke-GoServeProof @('shell','screencap','-p',"/data/local/tmp/$Name.png") | Out-Null
    Invoke-GoServeProof @('pull',"/data/local/tmp/$Name.png",(Join-Path $proofOutput "$Name.png"))
}
function Restore-GoServeSetting([string]$Scope,[string]$Key,[string]$Value){
    if($Value -eq 'null'){Invoke-GoServeProof @('shell','settings','delete',$Scope,$Key) | Out-Null}
    else{Invoke-GoServeProof @('shell','settings','put',$Scope,$Key,$Value) | Out-Null}
}
try{
    Invoke-GoServeProof @('shell','input','keyevent','KEYCODE_WAKEUP') | Out-Null
    Invoke-GoServeProof @('shell','wm','dismiss-keyguard') | Out-Null
    Invoke-GoServeProof @('shell','wm','size','780x1688') | Out-Null
    Invoke-GoServeProof @('shell','wm','density','320') | Out-Null
    Invoke-GoServeProof @('shell','settings','put','system','font_scale','1') | Out-Null
    Invoke-GoServeProof @('shell','settings','put','global','animator_duration_scale','1') | Out-Null
    Invoke-GoServeProof @('shell','am','force-stop','ph.edu.tip.goserve') | Out-Null
    Invoke-GoServeProof @('shell','am','start','-W','-n','ph.edu.tip.goserve/.ui.AuthActivity')
    Start-Sleep -Seconds 2
    Save-GoServeProof 'installed-auth'
    foreach($proofTheme in @('light','dark')){
        foreach($proofScreen in @('loading','welcome','conversation')){
            Invoke-GoServeProof @('shell','am','force-stop','ph.edu.tip.goserve') | Out-Null
            Invoke-GoServeProof @('shell','am','start','-W','-n','ph.edu.tip.goserve/.ui.SeryoUiPreviewActivity','--es','screen',$proofScreen,'--es','theme',$proofTheme)
            Start-Sleep -Seconds 2
            Save-GoServeProof "installed-$proofScreen-$proofTheme"
        }
    }
    Invoke-GoServeProof @('shell','screenrecord','--time-limit','6','--bit-rate','2000000','/data/local/tmp/goserve-final-seryo-idle.mp4')
    Invoke-GoServeProof @('pull','/data/local/tmp/goserve-final-seryo-idle.mp4',(Join-Path $proofOutput 'installed-seryo-idle.mp4'))
    Invoke-GoServeProof @('shell','settings','put','global','animator_duration_scale','0') | Out-Null
    Start-Sleep -Seconds 1
    Invoke-GoServeProof @('shell','screenrecord','--time-limit','3','--bit-rate','2000000','/data/local/tmp/goserve-final-seryo-reduced-motion.mp4')
    Invoke-GoServeProof @('pull','/data/local/tmp/goserve-final-seryo-reduced-motion.mp4',(Join-Path $proofOutput 'installed-seryo-reduced-motion.mp4'))
    Invoke-GoServeProof @('shell','getprop','ro.build.version.sdk')
    Invoke-GoServeProof @('shell','dumpsys','activity','activities') | Select-String 'mResumedActivity'
    Invoke-GoServeProof @('shell','logcat','-d','-t','150','-s','AndroidRuntime:E')
}finally{
    Restore-GoServeSetting 'global' 'animator_duration_scale' $proofMotion
    Restore-GoServeSetting 'system' 'font_scale' $proofFont
    if($proofSize -match 'Override size:\s*(\d+x\d+)'){Invoke-GoServeProof @('shell','wm','size',$Matches[1]) | Out-Null}else{Invoke-GoServeProof @('shell','wm','size','reset') | Out-Null}
    if($proofDensity -match 'Override density:\s*(\d+)'){Invoke-GoServeProof @('shell','wm','density',$Matches[1]) | Out-Null}else{Invoke-GoServeProof @('shell','wm','density','reset') | Out-Null}
}
Write-Output 'Installed debug smoke captures completed; display, font and motion settings restored. No account or business operation was performed.'
