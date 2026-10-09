param([Parameter(Mandatory=$true)][string]$Command)
$taskClient = [Net.Sockets.TcpClient]::new('127.0.0.1', 25577)
try {
    $taskStream = $taskClient.GetStream()
    $taskStream.ReadTimeout = 5000
    function Send-TaskPacket([int]$RequestId, [int]$Type, [string]$Body) {
        $taskBody = [Text.Encoding]::UTF8.GetBytes($Body)
        $taskPacket = [byte[]]::new($taskBody.Length + 14)
        [BitConverter]::GetBytes($taskBody.Length + 10).CopyTo($taskPacket, 0)
        [BitConverter]::GetBytes($RequestId).CopyTo($taskPacket, 4)
        [BitConverter]::GetBytes($Type).CopyTo($taskPacket, 8)
        $taskBody.CopyTo($taskPacket, 12)
        $taskStream.Write($taskPacket, 0, $taskPacket.Length)
    }
    function Read-TaskBytes([int]$Count) {
        $taskBytes = [byte[]]::new($Count)
        $taskOffset = 0
        while ($taskOffset -lt $Count) {
            $taskRead = $taskStream.Read($taskBytes, $taskOffset, $Count - $taskOffset)
            if ($taskRead -le 0) { throw 'Local validation RCON connection closed' }
            $taskOffset += $taskRead
        }
        return ,$taskBytes
    }
    function Read-TaskPacket {
        $taskLength = [BitConverter]::ToInt32((Read-TaskBytes 4), 0)
        if ($taskLength -lt 10 -or $taskLength -gt 1048576) { throw 'Invalid local RCON frame length' }
        $taskFrame = Read-TaskBytes $taskLength
        return [pscustomobject]@{
            Id = [BitConverter]::ToInt32($taskFrame, 0)
            Type = [BitConverter]::ToInt32($taskFrame, 4)
            Body = [Text.Encoding]::UTF8.GetString($taskFrame, 8, $taskLength - 10)
        }
    }
    Send-TaskPacket 7 3 'ultimate-finisher-local-verification'
    $taskAuth = Read-TaskPacket
    if ($taskAuth.Id -ne 7 -or $taskAuth.Type -ne 2) { throw 'Local validation RCON authentication failed' }
    Send-TaskPacket 8 2 $Command
    $taskResponse = Read-TaskPacket
    if ($taskResponse.Id -ne 8) { throw 'Unexpected local RCON response id' }
    Write-Output $taskResponse.Body
} finally {
    $taskClient.Dispose()
}
