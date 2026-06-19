param(
    [string]$OpcionesPath = "",
    [string]$OutputDir = "",
    [int]$AuxMaxRows = 300000,
    [switch]$NoAuxTables
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "Exportacion de vehiculos desde Gestion Trafico"
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
    param([xml]$Xml, [string]$XPath)
    $node = $Xml.SelectSingleNode($XPath)
    if ($null -eq $node) {
        return ""
    }
    return [string]$node.InnerText
}

function Parse-JtdsUrl {
    param([string]$Url, [string]$UserNode, [string]$PasswordNode)

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
    $serverName = $serverPart
    $port = "1433"
    if ($serverPart.Contains(":")) {
        $hostParts = $serverPart -split ":", 2
        $serverName = $hostParts[0]
        $port = $hostParts[1]
    }

    $instance = ""
    if ($params.ContainsKey("instance")) {
        $instance = $params["instance"]
    }
    $dataSource = if ($instance) { "$serverName\$instance" } else { "tcp:$serverName,$port" }

    $userName = $UserNode
    if (-not $userName -and $params.ContainsKey("user")) {
        $userName = $params["user"]
    }
    $password = $PasswordNode
    if (-not $password -and $params.ContainsKey("password")) {
        $password = $params["password"]
    }

    [pscustomobject]@{
        ServerHost = $serverName
        Port = $port
        DataSource = $dataSource
        Database = $database
        User = $userName
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
    $builder["Connect Timeout"] = 20
    return $builder.ConnectionString
}

function Invoke-SqlTable {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Sql,
        [hashtable]$Parameters = @{},
        [int]$TimeoutSeconds = 900
    )
    $command = $Connection.CreateCommand()
    $command.CommandText = $Sql
    $command.CommandTimeout = $TimeoutSeconds
    foreach ($key in $Parameters.Keys) {
        $parameter = $command.Parameters.Add("@$key", [System.Data.SqlDbType]::NVarChar, 4000)
        $parameter.Value = [string]$Parameters[$key]
    }
    $adapter = New-Object System.Data.SqlClient.SqlDataAdapter $command
    $table = New-Object System.Data.DataTable
    [void]$adapter.Fill($table)
    Write-Output -NoEnumerate $table
}

function Quote-SqlName {
    param([string]$Name)
    return "[" + $Name.Replace("]", "]]") + "]"
}

function Safe-FileName {
    param([string]$Name)
    return ($Name -replace '[\\/:*?"<>|]', "_")
}

function Export-DataTableCsv {
    param($Table, [string]$Path)
    if ($null -eq $Table) {
        "" | Set-Content -LiteralPath $Path -Encoding UTF8
        return
    }
    if ($Table.Rows.Count -eq 0) {
        $headers = @()
        foreach ($column in $Table.Columns) {
            $headers += '"' + ([string]$column.ColumnName).Replace('"', '""') + '"'
        }
        ($headers -join ",") | Set-Content -LiteralPath $Path -Encoding UTF8
        return
    }
    $rows = foreach ($row in $Table.Rows) {
        $item = [ordered]@{}
        foreach ($column in $Table.Columns) {
            $value = $row[$column]
            if ($value -is [System.DBNull]) {
                $value = $null
            }
            $item[$column.ColumnName] = $value
        }
        [pscustomobject]$item
    }
    $rows | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
}

function Export-Query {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Name,
        [string]$Sql,
        [string]$OutputDir,
        [string]$SummaryPath,
        [hashtable]$Parameters = @{},
        [int]$TimeoutSeconds = 900
    )
    $path = Join-Path $OutputDir "$Name.csv"
    try {
        $table = Invoke-SqlTable -Connection $Connection -Sql $Sql -Parameters $Parameters -TimeoutSeconds $TimeoutSeconds
        Export-DataTableCsv -Table $table -Path $path
        $line = "{0}: {1} filas" -f $Name, $table.Rows.Count
        Write-Host "  $line"
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $line
        Write-Output -NoEnumerate $table
    } catch {
        $line = "{0}: ERROR {1}" -f $Name, $_.Exception.Message
        Write-Host "  $line"
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $line
        Write-Output -NoEnumerate (New-Object System.Data.DataTable)
    }
}

function Get-TableColumns {
    param([System.Data.SqlClient.SqlConnection]$Connection, [string]$SchemaName, [string]$TableName)
    $sql = @"
select c.name as column_name
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
join sys.columns c on c.object_id = t.object_id
where s.name = @schema and t.name = @table
order by c.column_id
"@
    $table = Invoke-SqlTable -Connection $Connection -Sql $sql -Parameters @{ schema = $SchemaName; table = $TableName }
    $columns = @{}
    foreach ($row in $table.Rows) {
        $columns[[string]$row.column_name] = $true
    }
    return $columns
}

function Sql-Col {
    param([hashtable]$Columns, [string]$Source, [string]$Alias)
    if ($Columns.ContainsKey($Source)) {
        return "cast(v.$(Quote-SqlName $Source) as nvarchar(4000)) as $(Quote-SqlName $Alias)"
    }
    return "cast(null as nvarchar(4000)) as $(Quote-SqlName $Alias)"
}

function Sql-KeyNorm {
    param([hashtable]$Columns, [string]$Source)
    if ($Columns.ContainsKey($Source)) {
        return "upper(replace(replace(replace(replace(coalesce(cast(v.$(Quote-SqlName $Source) as nvarchar(100)), ''), '-', ''), ' ', ''), '.', ''), '/', ''))"
    }
    return "cast('' as nvarchar(100))"
}

function Sql-NullIfBlank {
    param([hashtable]$Columns, [string]$Source)
    if ($Columns.ContainsKey($Source)) {
        return "nullif(ltrim(rtrim(cast(v.$(Quote-SqlName $Source) as nvarchar(4000)))), '')"
    }
    return "cast(null as nvarchar(4000))"
}

$resolvedOpciones = Resolve-OpcionesPath -PathFromUser $OpcionesPath
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

if (-not $OutputDir) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $OutputDir = Join-Path $desktop "export_gestion_trafico_vehiculos_$timestamp"
}

$auxDir = Join-Path $OutputDir "aux_tablas_candidatas"
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null
New-Item -ItemType Directory -Force -Path $auxDir | Out-Null

Write-Host "Opciones.xml encontrado en:"
Write-Host "  $resolvedOpciones"

[xml]$xml = Get-Content -LiteralPath $resolvedOpciones -Raw -Encoding Default
$url = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_url"
$userNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_user"
$passwordNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_password"

$info = Parse-JtdsUrl -Url $url -UserNode $userNode -PasswordNode $passwordNode
$connectionString = New-ConnectionString -Info $info

$summaryPath = Join-Path $OutputDir "resumen.txt"
@(
    "Export Gestion Trafico - Vehiculos",
    "Fecha: $(Get-Date -Format s)",
    "Opciones: $resolvedOpciones",
    "Servidor: $($info.ServerHost)",
    "Puerto: $($info.Port)",
    "DataSource: $($info.DataSource)",
    "BaseDatos: $($info.Database)",
    "Usuario: ***",
    "Password: ***",
    "AuxMaxRows: $AuxMaxRows",
    "NoAuxTables: $NoAuxTables"
) | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "Conexion detectada:"
Write-Host "  Servidor: $($info.ServerHost)"
Write-Host "  Puerto:   $($info.Port)"
Write-Host "  Base:     $($info.Database)"
Write-Host "  Usuario:  ***"
Write-Host ""
Write-Host "Conectando a SQL Server..."

$connection = New-Object System.Data.SqlClient.SqlConnection $connectionString
try {
    $connection.Open()
    Write-Host "Conexion OK. Exportando vehiculos..."

    $vehicleColumns = Get-TableColumns -Connection $connection -SchemaName "dbo" -TableName "VEHICULOS"
    if ($vehicleColumns.Count -eq 0) {
        throw "No se encontro dbo.VEHICULOS o no contiene columnas visibles."
    }

    Export-Query -Connection $connection -Name "01_VEHICULOS_RAW" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select *
from dbo.VEHICULOS
order by CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoVehiculo
"@ | Out-Null

    $matriculaNorm = Sql-KeyNorm -Columns $vehicleColumns -Source "Matricula"
    $bastidorNorm = Sql-KeyNorm -Columns $vehicleColumns -Source "FTBastidor"
    $modeloSugerido = "coalesce($(Sql-NullIfBlank $vehicleColumns "FTMODELOTRANS"), $(Sql-NullIfBlank $vehicleColumns "FTMODELOMATR"), $(Sql-NullIfBlank $vehicleColumns "FTTipo"), $(Sql-NullIfBlank $vehicleColumns "FTTIPO_BASE"))"
    $normalizedSelect = @(
        (Sql-Col $vehicleColumns "CodigoColegio" "codigo_colegio"),
        (Sql-Col $vehicleColumns "NumeroColegiado" "numero_colegiado"),
        (Sql-Col $vehicleColumns "CodigoDespacho" "codigo_despacho"),
        (Sql-Col $vehicleColumns "CodigoVehiculo" "codigo_vehiculo"),
        "$matriculaNorm as [matricula_normalizada]",
        "$bastidorNorm as [bastidor_normalizado]",
        (Sql-Col $vehicleColumns "Matricula" "matricula"),
        (Sql-Col $vehicleColumns "FTBastidor" "bastidor"),
        (Sql-Col $vehicleColumns "FTMarca" "marca"),
        "$modeloSugerido as [modelo]",
        (Sql-Col $vehicleColumns "FTMODELOMATR" "modelo_matriculacion"),
        (Sql-Col $vehicleColumns "FTMODELOTRANS" "modelo_transmision"),
        (Sql-Col $vehicleColumns "FTTipo" "tipo"),
        (Sql-Col $vehicleColumns "FTVersion" "version"),
        (Sql-Col $vehicleColumns "FTMARCA_BASE" "marca_base"),
        (Sql-Col $vehicleColumns "FTTIPO_BASE" "tipo_base"),
        (Sql-Col $vehicleColumns "FTVERSION_BASE" "version_base"),
        (Sql-Col $vehicleColumns "FMATRICULACION" "fecha_matriculacion"),
        (Sql-Col $vehicleColumns "F1MATRICULACION" "fecha_primera_matriculacion"),
        (Sql-Col $vehicleColumns "AnoFabricacionVeh" "anyo_fabricacion"),
        (Sql-Col $vehicleColumns "FTCarburante" "carburante"),
        (Sql-Col $vehicleColumns "FTTIPOALIMENTACION" "tipo_alimentacion"),
        (Sql-Col $vehicleColumns "FTClasificacionVeh" "clasificacion_itv"),
        (Sql-Col $vehicleColumns "FTCODIGOITV" "codigo_itv"),
        (Sql-Col $vehicleColumns "CodigoTrafTipoVehiculo" "codigo_traf_tipo_vehiculo"),
        (Sql-Col $vehicleColumns "Codigo620TipoVehiculo" "codigo_620_tipo_vehiculo"),
        (Sql-Col $vehicleColumns "FTCategoria" "categoria"),
        (Sql-Col $vehicleColumns "FTServicio" "servicio"),
        (Sql-Col $vehicleColumns "FTServicioDestino" "servicio_destino"),
        (Sql-Col $vehicleColumns "FTPotencia" "potencia"),
        (Sql-Col $vehicleColumns "FTCilindrada" "cilindrada"),
        (Sql-Col $vehicleColumns "FTNCILINDROS" "numero_cilindros"),
        (Sql-Col $vehicleColumns "FTMasa" "masa"),
        (Sql-Col $vehicleColumns "FTTara" "tara"),
        (Sql-Col $vehicleColumns "FTPLAZAS" "plazas"),
        (Sql-Col $vehicleColumns "FTVELOCIDADMAX" "velocidad_maxima"),
        (Sql-Col $vehicleColumns "FITV" "fecha_itv"),
        (Sql-Col $vehicleColumns "RENTING" "renting"),
        (Sql-Col $vehicleColumns "HISTORICOSN" "historico_sn"),
        (Sql-Col $vehicleColumns "CARSHARINGSN" "carsharing_sn"),
        (Sql-Col $vehicleColumns "FechaAlta" "fecha_alta"),
        (Sql-Col $vehicleColumns "FechaModificacion" "fecha_modificacion")
    ) -join ",`n    "

    Export-Query -Connection $connection -Name "02_VEHICULOS_NORMALIZADOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select
    $normalizedSelect
from dbo.VEHICULOS v
order by v.CodigoColegio, v.NumeroColegiado, v.CodigoDespacho, v.CodigoVehiculo
"@ | Out-Null

    Export-Query -Connection $connection -Name "03_VEHICULOS_DUPLICADOS_POR_MATRICULA" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
with base as (
    select $matriculaNorm as matricula_normalizada
    from dbo.VEHICULOS v
)
select matricula_normalizada, count(*) as registros
from base
where matricula_normalizada <> ''
group by matricula_normalizada
having count(*) > 1
order by count(*) desc, matricula_normalizada
"@ | Out-Null

    Export-Query -Connection $connection -Name "04_VEHICULOS_SIN_MATRICULA" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select *
from dbo.VEHICULOS v
where $matriculaNorm = ''
order by CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoVehiculo
"@ | Out-Null

    Export-Query -Connection $connection -Name "05_VEHICULOS_DUPLICADOS_POR_BASTIDOR" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
with base as (
    select $bastidorNorm as bastidor_normalizado
    from dbo.VEHICULOS v
)
select bastidor_normalizado, count(*) as registros
from base
where bastidor_normalizado <> ''
group by bastidor_normalizado
having count(*) > 1
order by count(*) desc, bastidor_normalizado
"@ | Out-Null

    $catalogQueries = @(
        @{ Name = "101_VEHICULOS620TIPOVEHICULO"; Sql = "select * from dbo.VEHICULOS620TipoVehiculo" },
        @{ Name = "102_VEHICULOSTRAFTIPOVEHICULO"; Sql = "select * from dbo.VEHICULOSTrafTipoVehiculo" },
        @{ Name = "103_VEHICULOSTRAFTIPOCARBURANTE"; Sql = "select * from dbo.VEHICULOSTrafTipoCarburante" },
        @{ Name = "104_VEHICULOSAEATPROCUSADO"; Sql = "select * from dbo.VEHICULOSAEATProcUsado" }
    )
    foreach ($query in $catalogQueries) {
        Export-Query -Connection $connection -Name $query.Name -OutputDir $OutputDir -SummaryPath $summaryPath -Sql $query.Sql | Out-Null
    }

    Export-Query -Connection $connection -Name "90_TABLAS_CANDIDATAS_VEHICULOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select distinct
    s.name as schema_name,
    t.name as table_name,
    coalesce(row_counts.approx_rows, 0) as approx_rows
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
outer apply (
    select sum(p.rows) as approx_rows
    from sys.partitions p
    where p.object_id = t.object_id
      and p.index_id in (0, 1)
) row_counts
where
    t.name like '%VEHIC%'
    or t.name like '%MATRIC%'
    or t.name like '%BASTID%'
    or t.name like '%ITV%'
    or exists (
        select 1
        from sys.columns c
        where c.object_id = t.object_id
          and (
              c.name like '%VEHIC%'
              or c.name like '%MATRIC%'
              or c.name like '%BASTID%'
              or c.name like '%ITV%'
          )
    )
order by s.name, t.name
"@ | Out-Null

    Export-Query -Connection $connection -Name "91_COLUMNAS_CANDIDATAS_VEHICULOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select
    s.name as schema_name,
    t.name as table_name,
    c.column_id,
    c.name as column_name,
    ty.name as data_type,
    c.max_length,
    c.is_nullable
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
join sys.columns c on c.object_id = t.object_id
join sys.types ty on ty.user_type_id = c.user_type_id
where
    t.name like '%VEHIC%'
    or t.name like '%MATRIC%'
    or t.name like '%BASTID%'
    or t.name like '%ITV%'
    or c.name like '%VEHIC%'
    or c.name like '%MATRIC%'
    or c.name like '%BASTID%'
    or c.name like '%ITV%'
order by s.name, t.name, c.column_id
"@ | Out-Null

    if (-not $NoAuxTables) {
        Write-Host "Exportando tablas auxiliares candidatas hasta $AuxMaxRows filas por tabla..."
        $candidateTables = Invoke-SqlTable -Connection $connection -Sql @"
select distinct
    s.name as schema_name,
    t.name as table_name,
    coalesce(row_counts.approx_rows, 0) as approx_rows
from sys.tables t
join sys.schemas s on s.schema_id = t.schema_id
outer apply (
    select sum(p.rows) as approx_rows
    from sys.partitions p
    where p.object_id = t.object_id
      and p.index_id in (0, 1)
) row_counts
where
    t.name <> 'VEHICULOS'
    and (
        t.name like '%VEHIC%'
        or t.name like '%MATRIC%'
        or t.name like '%BASTID%'
        or t.name like '%ITV%'
    )
    and coalesce(row_counts.approx_rows, 0) <= @maxRows
order by s.name, t.name
"@ -Parameters @{ maxRows = $AuxMaxRows }

        foreach ($row in $candidateTables.Rows) {
            $schemaName = [string]$row.schema_name
            $tableName = [string]$row.table_name
            $safeName = Safe-FileName "$schemaName`_$tableName"
            $sql = "select * from $(Quote-SqlName $schemaName).$(Quote-SqlName $tableName)"
            Export-Query -Connection $connection -Name $safeName -OutputDir $auxDir -SummaryPath $summaryPath -Sql $sql | Out-Null
        }
    }

    $manifest = [ordered]@{
        generatedAt = (Get-Date -Format s)
        source = "Gestion Trafico"
        database = $info.Database
        files = @(
            "01_VEHICULOS_RAW.csv",
            "02_VEHICULOS_NORMALIZADOS.csv",
            "03_VEHICULOS_DUPLICADOS_POR_MATRICULA.csv",
            "04_VEHICULOS_SIN_MATRICULA.csv",
            "05_VEHICULOS_DUPLICADOS_POR_BASTIDOR.csv",
            "101_VEHICULOS620TIPOVEHICULO.csv",
            "102_VEHICULOSTRAFTIPOVEHICULO.csv",
            "103_VEHICULOSTRAFTIPOCARBURANTE.csv",
            "104_VEHICULOSAEATPROCUSADO.csv",
            "90_TABLAS_CANDIDATAS_VEHICULOS.csv",
            "91_COLUMNAS_CANDIDATAS_VEHICULOS.csv"
        )
        notes = @(
            "02_VEHICULOS_NORMALIZADOS es el CSV recomendado para importar como catalogo.",
            "Los catalogos 101-104 ayudan a traducir tipos DGT/620/carburante.",
            "aux_tablas_candidatas puede ayudar a detectar historicos y tablas complementarias."
        )
    }
    $manifest | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath (Join-Path $OutputDir "manifest.json") -Encoding UTF8

    $zipPath = "$OutputDir.zip"
    if (Test-Path -LiteralPath $zipPath) {
        Remove-Item -LiteralPath $zipPath -Force
    }
    Compress-Archive -Path (Join-Path $OutputDir "*") -DestinationPath $zipPath -Force

    Add-Content -LiteralPath $summaryPath -Encoding UTF8 -Value "ZIP: $zipPath"
    Write-Host ""
    Write-Host "EXPORTACION COMPLETADA"
    Write-Host "Carpeta:"
    Write-Host "  $OutputDir"
    Write-Host "ZIP:"
    Write-Host "  $zipPath"
} finally {
    if ($connection.State -ne [System.Data.ConnectionState]::Closed) {
        $connection.Close()
    }
}
