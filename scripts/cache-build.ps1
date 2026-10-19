function Get-BuildFingerprint {
    param([string[]]$Paths)
    $lines = foreach ($path in $Paths) {
        if (Test-Path -LiteralPath $path -PathType Container) {
            Get-ChildItem -LiteralPath $path -Recurse -File | Sort-Object FullName | ForEach-Object {
                $_.FullName + ':' + (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash
            }
        } elseif (Test-Path -LiteralPath $path -PathType Leaf) {
            $path + ':' + (Get-FileHash -LiteralPath $path -Algorithm SHA256).Hash
        }
    }
    [string[]]$lines = @($lines)
    [Array]::Sort($lines, [StringComparer]::Ordinal)
    $sha = [Security.Cryptography.SHA256]::Create()
    try { [BitConverter]::ToString($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes(($lines -join "`n")))) }
    finally { $sha.Dispose() }
}
