param(
    [string]$OpcionesPath = "",
    [string]$OutputDir = "",
    [int]$SampleRows = 0,
    [string]$Matricula = "",
    [int]$MatriculaMaxRows = 200
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "Iniciando exportacion de Gestion Trafico..."
Write-Host "No cierres esta ventana hasta que indique EXPORTACION COMPLETADA."
Write-Host ""

function Resolve-OpcionesPath {
    param([string]$PathFromUser)

    if ($PathFromUser -and (Test-Path -LiteralPath $PathFromUser)) {
        return (Resolve-Path -LiteralPath $PathFromUser).Path
    }

    $candidates = @(
        (Join-Path (Get-Location) "Opciones.xml"),
        (Join-Path $PSScriptRoot "Opciones.xml"),
        "C:\Program Files (x86)\Gestion Trafico 5.0\Opciones.xml",
        "C:\Program Files\Gestion Trafico 5.0\Opciones.xml"
    )

    foreach ($candidate in $candidates) {
        if (Test-Path -LiteralPath $candidate) {
            return (Resolve-Path -LiteralPath $candidate).Path
        }
    }

    throw "No encuentro Opciones.xml. Ejecuta el script con -OpcionesPath `"C:\ruta\Opciones.xml`""
}

function Get-XmlText {
    param(
        [xml]$Xml,
        [string]$XPath
    )
    $node = $Xml.SelectSingleNode($XPath)
    if ($null -eq $node) {
        return ""
    }
    return [string]$node.InnerText
}

function Parse-JtdsUrl {
    param(
        [string]$Url,
        [string]$UserNode,
        [string]$PasswordNode
    )

    $prefix = "jdbc:jtds:sqlserver://"
    if (-not $Url.StartsWith($prefix, [System.StringComparison]::OrdinalIgnoreCase)) {
        throw "La URL no parece jTDS SQL Server: $Url"
    }

    $rest = $Url.Substring($prefix.Length)
    $parts = $rest -split ";"
    $serverAndDb = $parts[0]
    $params = @{}

    foreach ($part in $parts | Select-Object -Skip 1) {
        if ($part -match "^([^=]+)=(.*)$") {
            $params[$matches[1].ToLowerInvariant()] = $matches[2]
        }
    }

    $slashIndex = $serverAndDb.IndexOf("/")
    if ($slashIndex -lt 0) {
        throw "No encuentro base de datos en la URL: $Url"
    }

    $serverPart = $serverAndDb.Substring(0, $slashIndex)
    $database = $serverAndDb.Substring($slashIndex + 1)
    $serverHost = $serverPart
    $port = "1433"

    if ($serverPart.Contains(":")) {
        $hostParts = $serverPart -split ":", 2
        $serverHost = $hostParts[0]
        $port = $hostParts[1]
    }

    $instance = ""
    if ($params.ContainsKey("instance")) {
        $instance = $params["instance"]
    }

    $dataSource = ""
    if ($instance) {
        $dataSource = "$serverHost\$instance"
    } else {
        $dataSource = "tcp:$serverHost,$port"
    }

    $user = $UserNode
    if (-not $user -and $params.ContainsKey("user")) {
        $user = $params["user"]
    }

    $password = $PasswordNode
    if (-not $password -and $params.ContainsKey("password")) {
        $password = $params["password"]
    }

    [pscustomobject]@{
        ServerHost = $serverHost
        Port = $port
        DataSource = $dataSource
        Database = $database
        User = $user
        Password = $password
    }
}

function New-ConnectionString {
    param([pscustomobject]$Info)

    $builder = New-Object System.Data.SqlClient.SqlConnectionStringBuilder
    $builder["Data Source"] = $Info.DataSource
    $builder["Initial Catalog"] = $Info.Database
    $builder["User ID"] = $Info.User
    $builder["Password"] = $Info.Password
    $builder["Encrypt"] = $false
    $builder["TrustServerCertificate"] = $true
    $builder["Connect Timeout"] = 15
    return $builder.ConnectionString
}

function Invoke-SqlTable {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Sql,
        [hashtable]$Parameters = @{}
    )

    $command = $Connection.CreateCommand()
    $command.CommandText = $Sql
    $command.CommandTimeout = 120

    foreach ($key in $Parameters.Keys) {
        $parameter = $command.Parameters.Add("@$key", [System.Data.SqlDbType]::NVarChar, 4000)
        $parameter.Value = [string]$Parameters[$key]
    }

    $adapter = New-Object System.Data.SqlClient.SqlDataAdapter $command
    $table = New-Object System.Data.DataTable
    [void]$adapter.Fill($table)
    return ,$table
}

function Quote-SqlName {
    param([string]$Name)
    return "[" + $Name.Replace("]", "]]") + "]"
}

function Safe-FileName {
    param([string]$Name)
    return ($Name -replace '[\\/:*?"<>|]', "_")
}

function Export-TableCsv {
    param(
        [System.Data.DataTable]$Table,
        [string]$Path
    )
    $Table | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
}

$resolvedOpciones = Resolve-OpcionesPath -PathFromUser $OpcionesPath
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

Write-Host "Opciones.xml encontrado en:"
Write-Host "  $resolvedOpciones"

if (-not $OutputDir) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $OutputDir = Join-Path $desktop "export_gestion_trafico_$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputDir "samples") | Out-Null
New-Item -ItemType Directory -Force -Path (Join-Path $OutputDir "matricula") | Out-Null

[xml]$xml = Get-Content -LiteralPath $resolvedOpciones -Raw -Encoding Default
$url = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_url"
$userNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_user"
$passwordNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_password"

$info = Parse-JtdsUrl -Url $url -UserNode $userNode -PasswordNode $passwordNode
$connectionString = New-ConnectionString -Info $info

Write-Host ""
Write-Host "Conexion detectada:"
Write-Host "  Servidor: $($info.ServerHost)"
Write-Host "  Puerto:   $($info.Port)"
Write-Host "  Base:     $($info.Database)"
Write-Host "  Usuario:  ***"
Write-Host ""
Write-Host "Conectando a SQL Server..."

$summaryPath = Join-Path $OutputDir "resumen.txt"
@(
    "Export Gestion Trafico",
    "Fecha: $(Get-Date -Format s)",
    "Opciones: $resolvedOpciones",
    "Servidor: $($info.ServerHost)",
    "Puerto: $($info.Port)",
    "DataSource: $($info.DataSource)",
    "BaseDatos: $($info.Database)",
    "Usuario: ***",
    "Password: ***",
    "SampleRows: $SampleRows",
    "Matricula: $Matricula"
) | Set-Content -LiteralPath $summaryPath -Encoding UTF8

$connection = New-Object System.Data.SqlClient.SqlConnection $connectionString
try {
    $connection.Open()
    Write-Host "Conexion OK. Leyendo esquema..."

    $tablesSql = @"
select
    s.name as schema_name,
    t.name as table_name,
    t.create_date,
    t.modify_date
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
order by s.name, t.name
"@

    $columnsSql = @"
select
    s.name as schema_name,
    t.name as table_name,
    c.column_id,
    c.name as column_name,
    ty.name as data_type,
    c.max_length,
    c.precision,
    c.scale,
    c.is_nullable,
    c.is_identity
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
join sys.columns c on c.object_id = t.object_id
join sys.types ty on ty.user_type_id = c.user_type_id
order by s.name, t.name, c.column_id
"@

    $primaryKeysSql = @"
select
    s.name as schema_name,
    t.name as table_name,
    kc.name as constraint_name,
    c.name as column_name,
    ic.key_ordinal
from sys.key_constraints kc
join sys.tables t on t.object_id = kc.parent_object_id
join sys.schemas s on s.schema_id = t.schema_id
join sys.index_columns ic on ic.object_id = t.object_id and ic.index_id = kc.unique_index_id
join sys.columns c on c.object_id = t.object_id and c.column_id = ic.column_id
where kc.type = 'PK'
order by s.name, t.name, ic.key_ordinal
"@

    $foreignKeysSql = @"
select
    fk.name as foreign_key_name,
    ps.name as parent_schema,
    pt.name as parent_table,
    pc.name as parent_column,
    rs.name as referenced_schema,
    rt.name as referenced_table,
    rc.name as referenced_column
from sys.foreign_keys fk
join sys.foreign_key_columns fkc on fkc.constraint_object_id = fk.object_id
join sys.tables pt on pt.object_id = fkc.parent_object_id
join sys.schemas ps on ps.schema_id = pt.schema_id
join sys.columns pc on pc.object_id = pt.object_id and pc.column_id = fkc.parent_column_id
join sys.tables rt on rt.object_id = fkc.referenced_object_id
join sys.schemas rs on rs.schema_id = rt.schema_id
join sys.columns rc on rc.object_id = rt.object_id and rc.column_id = fkc.referenced_column_id
order by ps.name, pt.name, fk.name
"@

    $tables = Invoke-SqlTable -Connection $connection -Sql $tablesSql
    $columns = Invoke-SqlTable -Connection $connection -Sql $columnsSql
    $primaryKeys = Invoke-SqlTable -Connection $connection -Sql $primaryKeysSql
    $foreignKeys = Invoke-SqlTable -Connection $connection -Sql $foreignKeysSql

    Export-TableCsv -Table $tables -Path (Join-Path $OutputDir "tables.csv")
    Export-TableCsv -Table $columns -Path (Join-Path $OutputDir "columns.csv")
    Export-TableCsv -Table $primaryKeys -Path (Join-Path $OutputDir "primary_keys.csv")
    Export-TableCsv -Table $foreignKeys -Path (Join-Path $OutputDir "foreign_keys.csv")

    Write-Host "Tablas encontradas: $($tables.Rows.Count)"
    Write-Host "Calculando conteos de filas..."

    $rowCounts = New-Object System.Data.DataTable
    [void]$rowCounts.Columns.Add("schema_name")
    [void]$rowCounts.Columns.Add("table_name")
    [void]$rowCounts.Columns.Add("row_count", [long])

    foreach ($row in $tables.Rows) {
        $schema = [string]$row.schema_name
        $tableName = [string]$row.table_name
        Write-Host "  Conteo: $schema.$tableName"
        $sql = "select count_big(*) as row_count from $(Quote-SqlName $schema).$(Quote-SqlName $tableName)"
        $countTable = Invoke-SqlTable -Connection $connection -Sql $sql
        $newRow = $rowCounts.NewRow()
        $newRow.schema_name = $schema
        $newRow.table_name = $tableName
        $newRow.row_count = [long]$countTable.Rows[0].row_count
        [void]$rowCounts.Rows.Add($newRow)
    }

    Export-TableCsv -Table $rowCounts -Path (Join-Path $OutputDir "row_counts.csv")

    if ($SampleRows -gt 0) {
        Write-Host "Exportando muestras de $SampleRows filas por tabla..."
        foreach ($row in $tables.Rows) {
            $schema = [string]$row.schema_name
            $tableName = [string]$row.table_name
            Write-Host "  Muestra: $schema.$tableName"
            $safeName = Safe-FileName "$schema.$tableName.csv"
            $sampleSql = "select top ($SampleRows) * from $(Quote-SqlName $schema).$(Quote-SqlName $tableName)"
            try {
                $sample = Invoke-SqlTable -Connection $connection -Sql $sampleSql
                Export-TableCsv -Table $sample -Path (Join-Path $OutputDir "samples\$safeName")
            } catch {
                Add-Content -LiteralPath $summaryPath -Encoding UTF8 -Value "No se pudo exportar muestra de $schema.$tableName : $($_.Exception.Message)"
            }
        }
    }

    if ($Matricula) {
        Write-Host "Buscando matricula: $Matricula"
        $matColsSql = @"
select
    s.name as schema_name,
    t.name as table_name,
    c.name as column_name
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
join sys.columns c on c.object_id = t.object_id
join sys.types ty on ty.user_type_id = c.user_type_id
where c.name like '%matric%'
  and ty.name in ('char','varchar','nchar','nvarchar','text','ntext')
order by s.name, t.name, c.name
"@
        $matCols = Invoke-SqlTable -Connection $connection -Sql $matColsSql
        Export-TableCsv -Table $matCols -Path (Join-Path $OutputDir "matricula_columns.csv")

        foreach ($row in $matCols.Rows) {
            $schema = [string]$row.schema_name
            $tableName = [string]$row.table_name
            $columnName = [string]$row.column_name
            Write-Host "  Buscando en: $schema.$tableName.$columnName"
            $safeName = Safe-FileName "$schema.$tableName.$columnName.csv"
            $sql = "select top ($MatriculaMaxRows) * from $(Quote-SqlName $schema).$(Quote-SqlName $tableName) where $(Quote-SqlName $columnName) like @matricula"
            try {
                $data = Invoke-SqlTable -Connection $connection -Sql $sql -Parameters @{ matricula = "%$Matricula%" }
                if ($data.Rows.Count -gt 0) {
                    Export-TableCsv -Table $data -Path (Join-Path $OutputDir "matricula\$safeName")
                }
            } catch {
                Add-Content -LiteralPath $summaryPath -Encoding UTF8 -Value "No se pudo buscar matricula en $schema.$tableName.$columnName : $($_.Exception.Message)"
            }
        }
    }

    Add-Content -LiteralPath $summaryPath -Encoding UTF8 -Value "Tablas exportadas: $($tables.Rows.Count)"
} finally {
    if ($connection.State -ne [System.Data.ConnectionState]::Closed) {
        $connection.Close()
    }
}

$zipPath = "$OutputDir.zip"
if (Test-Path -LiteralPath $zipPath) {
    Remove-Item -LiteralPath $zipPath -Force
}
Compress-Archive -LiteralPath (Join-Path $OutputDir "*") -DestinationPath $zipPath -Force

Write-Host ""
Write-Host "EXPORTACION COMPLETADA"
Write-Host "Carpeta: $OutputDir"
Write-Host "ZIP:     $zipPath"
Write-Host ""
Write-Host "Trae ese ZIP al proyecto para analizarlo aqui."
