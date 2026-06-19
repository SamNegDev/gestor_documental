param(
    [string]$OpcionesPath = "",
    [string]$OutputDir = "",
    [int]$AuxMaxRows = 200000,
    [switch]$NoAuxTables
)

$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "Exportacion de personas desde Gestion Trafico"
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

    $dataSource = ""
    if ($instance) {
        $dataSource = "$serverName\$instance"
    } else {
        $dataSource = "tcp:$serverName,$port"
    }

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
        [int]$TimeoutSeconds = 600
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
    param(
        $Table,
        [string]$Path
    )

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
        [int]$TimeoutSeconds = 600
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
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$SchemaName,
        [string]$TableName
    )

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
    param(
        [hashtable]$Columns,
        [string]$Source,
        [string]$Alias
    )
    return (Sql-ColAlias -Columns $Columns -TableAlias "p" -Source $Source -Alias $Alias)
}

function Sql-ColAlias {
    param(
        [hashtable]$Columns,
        [string]$TableAlias,
        [string]$Source,
        [string]$Alias
    )
    if ($Columns.ContainsKey($Source)) {
        return "cast($TableAlias.$(Quote-SqlName $Source) as nvarchar(4000)) as $(Quote-SqlName $Alias)"
    }
    return "cast(null as nvarchar(4000)) as $(Quote-SqlName $Alias)"
}

function Sql-NifNorm {
    param([hashtable]$Columns)
    return (Sql-NifNormAlias -Columns $Columns -TableAlias "p")
}

function Sql-NifNormAlias {
    param(
        [hashtable]$Columns,
        [string]$TableAlias
    )
    if ($Columns.ContainsKey("NIF")) {
        return "upper(replace(replace(replace(replace(coalesce(cast($TableAlias.[NIF] as nvarchar(100)), ''), '-', ''), ' ', ''), '.', ''), '/', ''))"
    }
    return "cast('' as nvarchar(100))"
}

$resolvedOpciones = Resolve-OpcionesPath -PathFromUser $OpcionesPath
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"

if (-not $OutputDir) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $OutputDir = Join-Path $desktop "export_gestion_trafico_personas_$timestamp"
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
    "Export Gestion Trafico - Personas",
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
    Write-Host "Conexion OK. Exportando personas..."

    $personasColumns = Get-TableColumns -Connection $connection -SchemaName "dbo" -TableName "PERSONAS"
    if ($personasColumns.Count -eq 0) {
        throw "No se encontro dbo.PERSONAS o no contiene columnas visibles."
    }

    Export-Query -Connection $connection -Name "01_PERSONAS_RAW" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select *
from dbo.PERSONAS
order by CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoPersona
"@ | Out-Null

    $nifNorm = Sql-NifNorm -Columns $personasColumns
    $normalizedSelect = @(
        (Sql-Col $personasColumns "CodigoColegio" "codigo_colegio"),
        (Sql-Col $personasColumns "NumeroColegiado" "numero_colegiado"),
        (Sql-Col $personasColumns "CodigoDespacho" "codigo_despacho"),
        (Sql-Col $personasColumns "CodigoPersona" "codigo_persona"),
        "$nifNorm as [nif_normalizado]",
        "case when left($nifNorm, 1) in ('A','B','C','D','E','F','G','H','J','N','P','Q','R','S','U','V','W') then 'JURIDICA' else 'FISICA' end as [tipo_persona_sugerido]",
        (Sql-Col $personasColumns "NIF" "nif"),
        (Sql-Col $personasColumns "Apellido1RazonSocial" "apellido1_razon_social"),
        (Sql-Col $personasColumns "Apellido2" "apellido2"),
        (Sql-Col $personasColumns "Nombre" "nombre"),
        (Sql-Col $personasColumns "Sexo" "sexo"),
        (Sql-Col $personasColumns "FECHANACIMIENTO" "fecha_nacimiento"),
        (Sql-Col $personasColumns "AUTONOMOSN" "autonomo_sn"),
        (Sql-Col $personasColumns "Anagrama" "anagrama"),
        (Sql-Col $personasColumns "Telef" "telefono"),
        (Sql-Col $personasColumns "TELEFMOVIL" "telefono_movil"),
        (Sql-Col $personasColumns "TELEFONO2" "telefono_2"),
        (Sql-Col $personasColumns "Fax" "fax"),
        (Sql-Col $personasColumns "Email" "email"),
        (Sql-Col $personasColumns "EMAILFACTURACION" "email_facturacion"),
        (Sql-Col $personasColumns "EMAILNOTIFICACIONES" "email_notificaciones"),
        (Sql-Col $personasColumns "Mail" "mail"),
        (Sql-Col $personasColumns "TIPODOCUMENTOSUSTITUTIVO" "tipo_documento_sustitutivo"),
        (Sql-Col $personasColumns "FCADUCNIF" "fecha_caducidad_documento"),
        (Sql-Col $personasColumns "NACIONALIDAD" "nacionalidad"),
        (Sql-Col $personasColumns "MANDATOFECHA" "mandato_fecha"),
        (Sql-Col $personasColumns "MANDATOREFERENCIA" "mandato_referencia"),
        (Sql-Col $personasColumns "MANDATOPRIMERAVEZSN" "mandato_primera_vez_sn"),
        (Sql-Col $personasColumns "DirSiglas" "dir_siglas"),
        (Sql-Col $personasColumns "DIRCALLE" "dir_calle"),
        (Sql-Col $personasColumns "DIRNUMERO" "dir_numero"),
        (Sql-Col $personasColumns "DIRKM" "dir_km"),
        (Sql-Col $personasColumns "DIRHECTOMETRO" "dir_hectometro"),
        (Sql-Col $personasColumns "DIRLETRA" "dir_letra"),
        (Sql-Col $personasColumns "DIRESC" "dir_escalera"),
        (Sql-Col $personasColumns "DIRPISO" "dir_piso"),
        (Sql-Col $personasColumns "DirPuerta" "dir_puerta"),
        (Sql-Col $personasColumns "DIRBLOQUE" "dir_bloque"),
        (Sql-Col $personasColumns "DirMunicipio" "dir_municipio"),
        (Sql-Col $personasColumns "DIRPUEBLO" "dir_pueblo"),
        (Sql-Col $personasColumns "DirProvincia" "dir_provincia"),
        (Sql-Col $personasColumns "DirCP" "dir_cp"),
        (Sql-Col $personasColumns "DIRPAIS" "dir_pais"),
        (Sql-Col $personasColumns "FechaAlta" "fecha_alta"),
        (Sql-Col $personasColumns "FechaModificacion" "fecha_modificacion"),
        (Sql-Col $personasColumns "FECHAULTMODIF" "fecha_ult_modif"),
        (Sql-Col $personasColumns "REPRCODIGOCOLEGIO" "repr_codigo_colegio"),
        (Sql-Col $personasColumns "REPRNUMEROCOLEGIADO" "repr_numero_colegiado"),
        (Sql-Col $personasColumns "REPRCODIGODESPACHO" "repr_codigo_despacho"),
        (Sql-Col $personasColumns "REPRCODIGOCLIENTE" "repr_codigo_persona"),
        (Sql-Col $personasColumns "ReprConcepto" "repr_concepto"),
        (Sql-Col $personasColumns "ReprDocAcreditacion" "repr_doc_acreditacion"),
        (Sql-Col $personasColumns "EMPCODIGOCOLEGIO" "emp_codigo_colegio"),
        (Sql-Col $personasColumns "EMPNUMEROCOLEGIADO" "emp_numero_colegiado"),
        (Sql-Col $personasColumns "EMPCODIGODESPACHO" "emp_codigo_despacho"),
        (Sql-Col $personasColumns "EMPCODIGOPERSONA" "emp_codigo_persona"),
        (Sql-Col $personasColumns "CODIGOPERSONAEXTERNO" "codigo_persona_externo")
    ) -join ",`n    "

    Export-Query -Connection $connection -Name "02_PERSONAS_NORMALIZADAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select
    $normalizedSelect
from dbo.PERSONAS p
order by p.CodigoColegio, p.NumeroColegiado, p.CodigoDespacho, p.CodigoPersona
"@ | Out-Null

    Export-Query -Connection $connection -Name "03_PERSONAS_DUPLICADAS_POR_NIF" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
with base as (
    select $nifNorm as nif_normalizado
    from dbo.PERSONAS p
)
select nif_normalizado, count(*) as registros
from base
where nif_normalizado <> ''
group by nif_normalizado
having count(*) > 1
order by count(*) desc, nif_normalizado
"@ | Out-Null

    Export-Query -Connection $connection -Name "04_PERSONAS_SIN_NIF" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select *
from dbo.PERSONAS p
where $nifNorm = ''
order by CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoPersona
"@ | Out-Null

    $empresaNifNorm = Sql-NifNormAlias -Columns $personasColumns -TableAlias "emp"
    $representanteNifNorm = Sql-NifNormAlias -Columns $personasColumns -TableAlias "rep"
    $reprWhereParts = @()
    foreach ($column in @("REPRCODIGOCOLEGIO", "REPRNUMEROCOLEGIADO", "REPRCODIGODESPACHO", "REPRCODIGOCLIENTE", "ReprConcepto", "ReprDocAcreditacion")) {
        if ($personasColumns.ContainsKey($column)) {
            $reprWhereParts += "nullif(ltrim(rtrim(cast(emp.$(Quote-SqlName $column) as nvarchar(4000)))), '') is not null"
        }
    }
    $reprWhere = "1 = 0"
    if ($reprWhereParts.Count -gt 0) {
        $reprWhere = $reprWhereParts -join "`n    or "
    }
    $representantesSelect = @(
        (Sql-ColAlias $personasColumns "emp" "CodigoColegio" "empresa_codigo_colegio"),
        (Sql-ColAlias $personasColumns "emp" "NumeroColegiado" "empresa_numero_colegiado"),
        (Sql-ColAlias $personasColumns "emp" "CodigoDespacho" "empresa_codigo_despacho"),
        (Sql-ColAlias $personasColumns "emp" "CodigoPersona" "empresa_codigo_persona"),
        "$empresaNifNorm as [empresa_nif_normalizado]",
        "case when left($empresaNifNorm, 1) in ('A','B','C','D','E','F','G','H','J','N','P','Q','R','S','U','V','W') then 'JURIDICA' else 'FISICA' end as [empresa_tipo_persona_sugerido]",
        (Sql-ColAlias $personasColumns "emp" "NIF" "empresa_nif"),
        (Sql-ColAlias $personasColumns "emp" "Apellido1RazonSocial" "empresa_apellido1_razon_social"),
        (Sql-ColAlias $personasColumns "emp" "Apellido2" "empresa_apellido2"),
        (Sql-ColAlias $personasColumns "emp" "Nombre" "empresa_nombre"),
        (Sql-ColAlias $personasColumns "emp" "REPRCODIGOCOLEGIO" "repr_codigo_colegio"),
        (Sql-ColAlias $personasColumns "emp" "REPRNUMEROCOLEGIADO" "repr_numero_colegiado"),
        (Sql-ColAlias $personasColumns "emp" "REPRCODIGODESPACHO" "repr_codigo_despacho"),
        (Sql-ColAlias $personasColumns "emp" "REPRCODIGOCLIENTE" "repr_codigo_persona"),
        (Sql-ColAlias $personasColumns "emp" "ReprConcepto" "repr_concepto"),
        (Sql-ColAlias $personasColumns "emp" "ReprDocAcreditacion" "repr_doc_acreditacion"),
        "$representanteNifNorm as [representante_nif_normalizado]",
        "case when $representanteNifNorm = '' then '' when left($representanteNifNorm, 1) in ('A','B','C','D','E','F','G','H','J','N','P','Q','R','S','U','V','W') then 'JURIDICA' else 'FISICA' end as [representante_tipo_persona_sugerido]",
        (Sql-ColAlias $personasColumns "rep" "NIF" "representante_nif"),
        (Sql-ColAlias $personasColumns "rep" "Apellido1RazonSocial" "representante_apellido1_razon_social"),
        (Sql-ColAlias $personasColumns "rep" "Apellido2" "representante_apellido2"),
        (Sql-ColAlias $personasColumns "rep" "Nombre" "representante_nombre"),
        (Sql-ColAlias $personasColumns "rep" "Sexo" "representante_sexo"),
        (Sql-ColAlias $personasColumns "rep" "FECHANACIMIENTO" "representante_fecha_nacimiento"),
        (Sql-ColAlias $personasColumns "rep" "TIPODOCUMENTOSUSTITUTIVO" "representante_tipo_documento_sustitutivo"),
        (Sql-ColAlias $personasColumns "rep" "FCADUCNIF" "representante_fecha_caducidad_documento"),
        (Sql-ColAlias $personasColumns "rep" "NACIONALIDAD" "representante_nacionalidad"),
        (Sql-ColAlias $personasColumns "rep" "DirSiglas" "representante_dir_siglas"),
        (Sql-ColAlias $personasColumns "rep" "DIRCALLE" "representante_dir_calle"),
        (Sql-ColAlias $personasColumns "rep" "DIRNUMERO" "representante_dir_numero"),
        (Sql-ColAlias $personasColumns "rep" "DIRKM" "representante_dir_km"),
        (Sql-ColAlias $personasColumns "rep" "DIRHECTOMETRO" "representante_dir_hectometro"),
        (Sql-ColAlias $personasColumns "rep" "DIRLETRA" "representante_dir_letra"),
        (Sql-ColAlias $personasColumns "rep" "DIRESC" "representante_dir_escalera"),
        (Sql-ColAlias $personasColumns "rep" "DIRPISO" "representante_dir_piso"),
        (Sql-ColAlias $personasColumns "rep" "DirPuerta" "representante_dir_puerta"),
        (Sql-ColAlias $personasColumns "rep" "DIRBLOQUE" "representante_dir_bloque"),
        (Sql-ColAlias $personasColumns "rep" "DirMunicipio" "representante_dir_municipio"),
        (Sql-ColAlias $personasColumns "rep" "DIRPUEBLO" "representante_dir_pueblo"),
        (Sql-ColAlias $personasColumns "rep" "DirProvincia" "representante_dir_provincia"),
        (Sql-ColAlias $personasColumns "rep" "DirCP" "representante_dir_cp"),
        (Sql-ColAlias $personasColumns "rep" "DIRPAIS" "representante_dir_pais"),
        (Sql-ColAlias $personasColumns "rep" "Telef" "representante_telefono"),
        (Sql-ColAlias $personasColumns "rep" "TELEFMOVIL" "representante_telefono_movil"),
        (Sql-ColAlias $personasColumns "rep" "TELEFONO2" "representante_telefono_2"),
        (Sql-ColAlias $personasColumns "rep" "Email" "representante_email"),
        (Sql-ColAlias $personasColumns "rep" "EMAILFACTURACION" "representante_email_facturacion"),
        (Sql-ColAlias $personasColumns "rep" "EMAILNOTIFICACIONES" "representante_email_notificaciones")
    ) -join ",`n    "

    Export-Query -Connection $connection -Name "05_PERSONAS_REPRESENTANTES" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
select
    $representantesSelect
from dbo.PERSONAS emp
left join dbo.PERSONAS rep on rep.CodigoColegio = emp.REPRCODIGOCOLEGIO
    and rep.NumeroColegiado = emp.REPRNUMEROCOLEGIADO
    and rep.CodigoDespacho = emp.REPRCODIGODESPACHO
    and rep.CodigoPersona = emp.REPRCODIGOCLIENTE
where
    $reprWhere
order by emp.CodigoColegio, emp.NumeroColegiado, emp.CodigoDespacho, emp.CodigoPersona
"@ | Out-Null

    Export-Query -Connection $connection -Name "90_TABLAS_CANDIDATAS_PERSONAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
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
    t.name like '%PERSON%'
    or t.name like '%CLIENT%'
    or t.name like '%REPRES%'
    or t.name like '%ADMIN%'
    or t.name like '%APODER%'
    or t.name like '%CONTACT%'
    or t.name like '%DIREC%'
    or exists (
        select 1
        from sys.columns c
        where c.object_id = t.object_id
          and (
              c.name like '%PERSON%'
              or c.name like '%REPRES%'
              or c.name like '%ADMIN%'
              or c.name like '%APODER%'
          )
    )
order by s.name, t.name
"@ | Out-Null

    Export-Query -Connection $connection -Name "91_COLUMNAS_CANDIDATAS_PERSONAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Sql @"
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
    t.name like '%PERSON%'
    or t.name like '%CLIENT%'
    or t.name like '%REPRES%'
    or t.name like '%ADMIN%'
    or t.name like '%APODER%'
    or t.name like '%CONTACT%'
    or t.name like '%DIREC%'
    or c.name like '%PERSON%'
    or c.name like '%REPRES%'
    or c.name like '%ADMIN%'
    or c.name like '%APODER%'
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
    t.name <> 'PERSONAS'
    and (
        t.name like '%REPRES%'
        or t.name like '%ADMIN%'
        or t.name like '%APODER%'
        or t.name like '%PERSONAS%'
        or exists (
            select 1
            from sys.columns c
            where c.object_id = t.object_id
              and (
                  c.name like '%REPRES%'
                  or c.name like '%ADMIN%'
                  or c.name like '%APODER%'
              )
        )
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
            "01_PERSONAS_RAW.csv",
            "02_PERSONAS_NORMALIZADAS.csv",
            "03_PERSONAS_DUPLICADAS_POR_NIF.csv",
            "04_PERSONAS_SIN_NIF.csv",
            "05_PERSONAS_REPRESENTANTES.csv",
            "90_TABLAS_CANDIDATAS_PERSONAS.csv",
            "91_COLUMNAS_CANDIDATAS_PERSONAS.csv"
        )
        notes = @(
            "No se exportan contrasenas en resumen.",
            "02_PERSONAS_NORMALIZADAS es el CSV recomendado para importar como catalogo.",
            "05_PERSONAS_REPRESENTANTES cruza empresas/personas con su representante guardado en los campos REPR de PERSONAS.",
            "aux_tablas_candidatas puede ayudar a detectar administradores/representantes si existen tablas especificas."
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
