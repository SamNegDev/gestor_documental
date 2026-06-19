param(
    [string]$OpcionesPath = "",
    [string]$OutputDir = "",
    [string]$Matricula = "",
    [int]$MaxRows = 500
)

$ErrorActionPreference = "Stop"

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
    $command.CommandTimeout = 180

    foreach ($key in $Parameters.Keys) {
        $parameter = $command.Parameters.Add("@$key", [System.Data.SqlDbType]::NVarChar, 4000)
        $parameter.Value = [string]$Parameters[$key]
    }

    $adapter = New-Object System.Data.SqlClient.SqlDataAdapter $command
    $table = New-Object System.Data.DataTable
    [void]$adapter.Fill($table)
    return ,$table
}

function Export-TableCsv {
    param(
        [System.Data.DataTable]$Table,
        [string]$Path
    )
    $Table | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
}

function Export-Query {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [string]$Name,
        [string]$Sql,
        [hashtable]$Parameters,
        [string]$OutputDir,
        [string]$SummaryPath
    )

    $path = Join-Path $OutputDir "$Name.csv"
    try {
        $table = Invoke-SqlTable -Connection $Connection -Sql $Sql -Parameters $Parameters
        Export-TableCsv -Table $table -Path $path
        $line = "{0}: {1} filas" -f $Name, $table.Rows.Count
        Write-Host "  $line"
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $line
        return ,$table
    } catch {
        $line = "{0}: ERROR {1}" -f $Name, $_.Exception.Message
        Write-Host "  $line"
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $line
        return ,(New-Object System.Data.DataTable)
    }
}

function Export-ObjectsCsv {
    param(
        [object[]]$Objects,
        [string]$Path
    )

    $items = @($Objects)
    if ($items.Count -eq 0) {
        "" | Set-Content -LiteralPath $Path -Encoding UTF8
        return
    }
    $items | Export-Csv -LiteralPath $Path -NoTypeInformation -Encoding UTF8
}

function Normalize-Key {
    param($Value)
    if ($null -eq $Value) {
        return ""
    }
    return ([string]$Value).ToUpperInvariant() -replace '[^0-9A-Z]', ''
}

function Try-DecimalValue {
    param($Value)
    if ([string]::IsNullOrWhiteSpace([string]$Value)) {
        return $null
    }
    $raw = ([string]$Value).Trim().Replace(",", ".")
    $number = [decimal]0
    if ([decimal]::TryParse($raw, [System.Globalization.NumberStyles]::Float, [System.Globalization.CultureInfo]::InvariantCulture, [ref]$number)) {
        return $number
    }
    return $null
}

function Get-DateYear {
    param($Value)
    if ([string]::IsNullOrWhiteSpace([string]$Value)) {
        return $null
    }
    $dt = [datetime]::MinValue
    foreach ($culture in @([System.Globalization.CultureInfo]::GetCultureInfo("es-ES"), [System.Globalization.CultureInfo]::InvariantCulture, [System.Globalization.CultureInfo]::CurrentCulture)) {
        if ([datetime]::TryParse([string]$Value, $culture, [System.Globalization.DateTimeStyles]::AssumeLocal, [ref]$dt)) {
            return $dt.Year
        }
    }
    return $null
}

function Get-CommercialPeriod {
    param($Caracteristicas)

    $text = [string]$Caracteristicas
    $start = $null
    $end = $null
    if ($text -match '(?i)A.OS\s*:\s*(\d{4})\s*-\s*(\d{4})?') {
        $start = [int]$matches[1]
        if ($matches.Count -ge 3 -and -not [string]::IsNullOrWhiteSpace($matches[2])) {
            $end = [int]$matches[2]
        }
    }

    [pscustomobject]@{
        Inicio = $start
        Fin = $end
    }
}

function Test-Period {
    param(
        $Year,
        $Inicio,
        $Fin
    )
    if ($null -eq $Year -or $null -eq $Inicio) {
        return $false
    }
    if ($Year -lt $Inicio) {
        return $false
    }
    if ($null -ne $Fin -and $Fin -gt 0 -and $Year -gt $Fin) {
        return $false
    }
    return $true
}

function Get-SearchTypes {
    param($Tt)

    $type = ([string]$Tt).Trim().ToUpperInvariant()
    if ($type -eq "X") {
        return @("T")
    }
    if ($type -eq "T") {
        return @("T", "C")
    }
    if ($type -eq "C") {
        return @("C", "T")
    }
    return @($type)
}

function Export-Valuation620 {
    param(
        [System.Data.SqlClient.SqlConnection]$Connection,
        [System.Data.DataTable]$VehicleRows,
        [System.Data.DataTable]$TransmissionRows,
        [string]$OutputDir,
        [string]$SummaryPath
    )

    $candidatePath = Join-Path $OutputDir "16_VALORACION_CANDIDATOS_620.csv"
    $selectedPath = Join-Path $OutputDir "17_VALORACION_SELECCIONADA_620.csv"

    if ($VehicleRows.Rows.Count -eq 0) {
        Export-ObjectsCsv -Objects @() -Path $candidatePath
        Export-ObjectsCsv -Objects @() -Path $selectedPath
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value "16_VALORACION_CANDIDATOS_620: 0 filas"
        Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value "17_VALORACION_SELECCIONADA_620: 0 filas"
        return
    }

    $vehicle = $VehicleRows.Rows[0]
    $transmission = if ($TransmissionRows.Rows.Count -gt 0) { $TransmissionRows.Rows[0] } else { $null }

    $catalog = Invoke-SqlTable -Connection $Connection -Sql "select * from dbo.VEHICULOS620TipoVehiculo"
    $tipo620 = $catalog.Rows | Where-Object { [string]$_.CODIGO620TIPOVEHICULO -eq [string]$vehicle.Codigo620TipoVehiculo } | Select-Object -First 1
    $tt = if ($tipo620) { [string]$tipo620.TT } else { "" }

    $valuationYear = if ($transmission -and -not [string]::IsNullOrWhiteSpace([string]$transmission.IMP620ANYOVALORACION)) {
        [string]$transmission.IMP620ANYOVALORACION
    } else {
        [string](Get-Date).Year
    }
    $registrationYear = Get-DateYear $(if (-not [string]::IsNullOrWhiteSpace([string]$vehicle.F1MATRICULACION)) { $vehicle.F1MATRICULACION } else { $vehicle.FMATRICULACION })
    $brand = [string]$vehicle.FTMarca
    $model = if (-not [string]::IsNullOrWhiteSpace([string]$vehicle.FTMODELOMATR)) { [string]$vehicle.FTMODELOMATR } else { [string]$vehicle.FTMODELOTRANS }
    $brandKey = Normalize-Key $brand
    $modelKey = Normalize-Key $model
    $vehicleCylinder = Try-DecimalValue $vehicle.FTCilindrada
    $vehiclePower = Try-DecimalValue $vehicle.FTPotencia

    $candidates = New-Object System.Collections.ArrayList

    if ($tt -in @("M", "S")) {
        $motoRows = Invoke-SqlTable -Connection $Connection -Sql "select * from dbo.VEHICULOSValoracionesMoto where TIPO = @tipo" -Parameters @{ tipo = $tt }
        foreach ($row in $motoRows.Rows) {
            $min = Try-DecimalValue $row.CILINDRADAMIN
            $max = Try-DecimalValue $row.CILINDRADAMAX
            $cylinderOk = $false
            if ($null -ne $vehicleCylinder -and $null -ne $min -and $null -ne $max) {
                $cylinderOk = ($vehicleCylinder -ge $min -and $vehicleCylinder -le $max)
            }
            [void]$candidates.Add([pscustomobject]@{
                Fuente = "VEHICULOSValoracionesMoto"
                SeleccionManual = $true
                MotivoManual = "Motocicletas/ciclomotores: marca/modelo y valoracion se confirman manualmente por fecha y cilindrada"
                PrioridadTipo = 0
                TipoBusqueda = "manual_moto_ciclomotor"
                TT = [string]$row.TIPO
                Marca = $brand
                Modelo = $model
                Carasteristicas = ""
                ANO = $valuationYear
                PeriodoInicio = ""
                PeriodoFin = ""
                AnyoMatriculacion = $registrationYear
                PeriodoOk = $true
                ModeloOk = $true
                CilindradaVehiculo = $vehicleCylinder
                CilindradaValoracion = "$($row.CILINDRADAMIN)-$($row.CILINDRADAMAX)"
                CilindradaOk = $cylinderOk
                CilindradaDiferencia = ""
                PotenciaDiferencia = ""
                CarburanteOk = $true
                VALORFISCAL1ER = $row.VALORFISCAL
                IDSiga = ""
                CodigoMarcaExt = ""
                CodigoModeloExt = ""
                CODIGOVALORCIONVEHI = $row.CODIGOVALORACIONESMOTO
            })
        }
    } else {
        $rawCandidates = Invoke-SqlTable -Connection $Connection -Sql @"
select top (5000) *
from dbo.VEHICULOSValoraciones620
where ANO = @anyo
  and upper(Marca) like @marca
order by VALORFISCAL1ER asc
"@ -Parameters @{ anyo = $valuationYear; marca = "%$brand%" }

        $searchTypes = @(Get-SearchTypes $tt)
        foreach ($row in $rawCandidates.Rows) {
            $candidateTt = ([string]$row.TT).Trim().ToUpperInvariant()
            $typePriority = [Array]::IndexOf($searchTypes, $candidateTt)
            if ($typePriority -lt 0) {
                continue
            }

            $period = Get-CommercialPeriod $row.Carasteristicas
            $periodOk = Test-Period -Year $registrationYear -Inicio $period.Inicio -Fin $period.Fin
            $candidateModelKey = Normalize-Key $row.Modelo
            $modelOk = $true
            if (-not [string]::IsNullOrWhiteSpace($modelKey)) {
                $modelOk = ($candidateModelKey.Contains($modelKey) -or $modelKey.Contains($candidateModelKey))
            }

            $candidateCylinder = Try-DecimalValue $row.Cilindrada
            $candidatePower = Try-DecimalValue $row.Potencia
            $cylinderDiff = if ($null -ne $vehicleCylinder -and $null -ne $candidateCylinder) { [Math]::Abs($vehicleCylinder - $candidateCylinder) } else { $null }
            $powerDiff = if ($null -ne $vehiclePower -and $null -ne $candidatePower) { [Math]::Abs($vehiclePower - $candidatePower) } else { $null }
            $carburanteOk = $true
            if (-not [string]::IsNullOrWhiteSpace([string]$vehicle.FTCarburante) -and -not [string]::IsNullOrWhiteSpace([string]$row.CARBURANTE)) {
                $carburanteOk = ([string]$vehicle.FTCarburante).Trim().ToUpperInvariant() -eq ([string]$row.CARBURANTE).Trim().ToUpperInvariant()
            }

            [void]$candidates.Add([pscustomobject]@{
                Fuente = "VEHICULOSValoraciones620"
                SeleccionManual = $false
                MotivoManual = ""
                PrioridadTipo = $typePriority
                TipoBusqueda = if ($typePriority -eq 0) { "principal" } else { "fallback_turismo_comercial" }
                TT = $candidateTt
                Marca = $row.Marca
                Modelo = $row.Modelo
                Carasteristicas = $row.Carasteristicas
                ANO = $row.ANO
                PeriodoInicio = $period.Inicio
                PeriodoFin = $period.Fin
                AnyoMatriculacion = $registrationYear
                PeriodoOk = $periodOk
                ModeloOk = $modelOk
                CilindradaVehiculo = $vehicleCylinder
                CilindradaValoracion = $row.Cilindrada
                CilindradaOk = if ($null -eq $cylinderDiff) { $true } else { $cylinderDiff -le 25 }
                CilindradaDiferencia = $cylinderDiff
                PotenciaDiferencia = $powerDiff
                CarburanteOk = $carburanteOk
                VALORFISCAL1ER = $row.VALORFISCAL1ER
                IDSiga = $row.IDSiga
                CodigoMarcaExt = $row.CodigoMarcaExt
                CodigoModeloExt = $row.CodigoModeloExt
                CODIGOVALORCIONVEHI = $row.CODIGOVALORCIONVEHI
            })
        }
    }

    $candidateItems = @($candidates.ToArray())
    Export-ObjectsCsv -Objects $candidateItems -Path $candidatePath

    $selected = @()
    if ($candidateItems.Count -gt 0) {
        if ($tt -in @("M", "S")) {
            $selected = @($candidateItems |
                Where-Object { $_.CilindradaOk -eq $true } |
                Sort-Object @{Expression = {[decimal]($_.VALORFISCAL1ER)}; Ascending = $true} |
                Select-Object -First 1)
        } else {
            $valid = @($candidateItems | Where-Object { $_.PeriodoOk -eq $true -and $_.ModeloOk -eq $true -and $_.CilindradaOk -eq $true -and $_.CarburanteOk -eq $true })
            if ($valid.Count -gt 0) {
                $bestPriority = ($valid | Measure-Object -Property PrioridadTipo -Minimum).Minimum
                $selected = @($valid |
                    Where-Object { $_.PrioridadTipo -eq $bestPriority } |
                    Sort-Object @{Expression = {[decimal]($_.VALORFISCAL1ER)}; Ascending = $true}, @{Expression = {[decimal]($_.CilindradaDiferencia)}; Ascending = $true} |
                    Select-Object -First 1)
            }
        }
    }

    Export-ObjectsCsv -Objects $selected -Path $selectedPath

    $lineCandidates = "16_VALORACION_CANDIDATOS_620: {0} filas" -f $candidateItems.Count
    $lineSelected = "17_VALORACION_SELECCIONADA_620: {0} filas" -f @($selected).Count
    Write-Host "  $lineCandidates"
    Write-Host "  $lineSelected"
    Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $lineCandidates
    Add-Content -LiteralPath $SummaryPath -Encoding UTF8 -Value $lineSelected
}

if (-not $Matricula) {
    throw "Indica una matricula con -Matricula"
}

$safeMatricula = ($Matricula -replace '[^0-9A-Za-z]', '').ToUpperInvariant()
if (-not $safeMatricula) {
    throw "La matricula indicada no contiene letras o numeros validos"
}

$limit = [Math]::Min([Math]::Max([int]$MaxRows, 1), 2000)
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$resolvedOpciones = Resolve-OpcionesPath -PathFromUser $OpcionesPath

if (-not $OutputDir) {
    $desktop = [Environment]::GetFolderPath("Desktop")
    $OutputDir = Join-Path $desktop "export_gestion_trafico_relacion_${safeMatricula}_$timestamp"
}

New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

[xml]$xml = Get-Content -LiteralPath $resolvedOpciones -Raw -Encoding Default
$url = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_url"
$userNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_user"
$passwordNode = Get-XmlText -Xml $xml -XPath "/root/CONEXIONES/conexion/SimpleDataSource_password"

$info = Parse-JtdsUrl -Url $url -UserNode $userNode -PasswordNode $passwordNode
$connectionString = New-ConnectionString -Info $info
$summaryPath = Join-Path $OutputDir "resumen.txt"

@(
    "Export Gestion Trafico relacionado por matricula",
    "Fecha: $(Get-Date -Format s)",
    "Opciones: $resolvedOpciones",
    "Servidor: $($info.ServerHost)",
    "Puerto: $($info.Port)",
    "DataSource: $($info.DataSource)",
    "BaseDatos: $($info.Database)",
    "Usuario: ***",
    "Password: ***",
    "Matricula: $safeMatricula",
    "MaxRows: $limit",
    "",
    "Resultados:"
) | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "Exportacion relacionada por matricula"
Write-Host "Matricula: $safeMatricula"
Write-Host "Salida: $OutputDir"
Write-Host ""

$params = @{
    matriculaNormalizada = $safeMatricula
    matriculaLike = "%$safeMatricula%"
}

$vehicleCte = @"
v as (
    select top ($limit) *
    from dbo.VEHICULOS
    where
        replace(replace(replace(upper(isnull(Matricula, '')), ' ', ''), '-', ''), '.', '') = @matriculaNormalizada
        or upper(isnull(Matricula, '')) like @matriculaLike
)
"@

$transmissionCte = @"
$vehicleCte,
tr as (
    select top ($limit) t.*
    from dbo.TRANSMISIONES t
    join v on
        t.VEHICodigoColegio = v.CodigoColegio
        and t.VEHINumeroColegiado = v.NumeroColegiado
        and t.VEHICodigoDespacho = v.CodigoDespacho
        and t.VEHICodigoVehiculo = v.CodigoVehiculo
)
"@

$expedienteCte = @"
$transmissionCte,
exp_keys as (
    select
        e.CodigoColegio,
        e.NumeroColegiado,
        e.CodigoDespacho,
        e.CodigoExpedienteInter
    from dbo.EXPEDIENTES e
    join v on
        e.VEHICodigoColegio = v.CodigoColegio
        and e.VEHINumeroColegiado = v.NumeroColegiado
        and e.VEHICodigoDespacho = v.CodigoDespacho
        and e.VEHICodigoVehiculo = v.CodigoVehiculo
    union
    select
        e.CodigoColegio,
        e.NumeroColegiado,
        e.CodigoDespacho,
        e.CodigoExpedienteInter
    from dbo.EXPEDIENTES e
    join tr on
        e.CodigoExpedienteInter = tr.ExpCodigoExpedienteInter
        and e.CodigoColegio = tr.ExpCodigoColegio
        and e.NumeroColegiado = tr.ExpNumeroColegiado
        and e.CodigoDespacho = tr.ExpCodigoDespacho
),
exp as (
    select top ($limit) e.*
    from dbo.EXPEDIENTES e
    join exp_keys k on
        e.CodigoColegio = k.CodigoColegio
        and e.NumeroColegiado = k.NumeroColegiado
        and e.CodigoDespacho = k.CodigoDespacho
        and e.CodigoExpedienteInter = k.CodigoExpedienteInter
)
"@

$connection = New-Object System.Data.SqlClient.SqlConnection $connectionString
try {
    Write-Host "Conectando a SQL Server..."
    $connection.Open()
    Write-Host "Conexion OK. Exportando tablas relacionadas..."

    $vehicleRows = Export-Query -Connection $connection -Name "01_VEHICULOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $vehicleCte
select * from v
"@

    if ($vehicleRows.Rows.Count -eq 0) {
        throw "No se encontro ningun vehiculo para la matricula $safeMatricula"
    }

    Export-Query -Connection $connection -Name "02_PERSONAS_RELACIONADAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $transmissionCte,
rel as (
    select TitCodigoColegio as CodigoColegio, TitNumeroColegiado as NumeroColegiado, TitCodigoDespacho as CodigoDespacho, TitCodigoPersona as CodigoPersona from v
    union
    select VENDCodigoColegio, VENDNumeroColegiado, VENDCodigoDespacho, VENDCodigoPersona from v
    union
    select IMPCodigoColegio, IMPNumeroColegiado, IMPCodigoDespacho, IMPCodigoPersona from v
    union
    select CAPCODIGOCOLEGIO, CAPNUMEROCOLEGIADO, CAPCODIGODESPACHO, CAPCODIGOPERSONA from v
    union
    select ADQCodigoColegio, ADQNumeroColegiado, ADQCodigoDespacho, ADQCodigoPersona from tr
    union
    select TRANSCodigoColegio, TRANSNumeroColegiado, TRANSCodigoDespacho, TRANSCodigoPersona from tr
    union
    select PRECodigoColegio, PRENumeroColegiado, PRECodigoDespacho, PRECodigoPersona from tr
    union
    select COMPRVENTCodigoColegio, COMPRVENTNumeroColegiado, COMPRVENTCodigoDespacho, COMPRVENTCodigoPersona from tr
),
rel_keys as (
    select CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoPersona
    from rel
    where CodigoPersona is not null
    group by CodigoColegio, NumeroColegiado, CodigoDespacho, CodigoPersona
)
select p.*
from dbo.PERSONAS p
join rel_keys on
    p.CodigoColegio = rel_keys.CodigoColegio
    and p.NumeroColegiado = rel_keys.NumeroColegiado
    and p.CodigoDespacho = rel_keys.CodigoDespacho
    and p.CodigoPersona = rel_keys.CodigoPersona
"@ | Out-Null

    $transmissionRows = Export-Query -Connection $connection -Name "03_TRANSMISIONES" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $transmissionCte
select * from tr
"@

    Export-Query -Connection $connection -Name "04_EXPEDIENTES" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $expedienteCte
select * from exp
"@ | Out-Null

    Export-Query -Connection $connection -Name "05_TRANSMISIONATRIB" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $transmissionCte
select a.*
from dbo.TRANSMISIONATRIB a
join tr on
    a.CODIGOCOLEGIO = tr.CodigoColegio
    and a.NUMEROCOLEGIADO = tr.NumeroColegiado
    and a.CODIGODESPACHO = tr.CodigoDespacho
    and a.CODIGOTRANSMISION = tr.CODIGOTRANSMISION
"@ | Out-Null

    Export-Query -Connection $connection -Name "06_TRANSMISIONESDOCUMENTOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $transmissionCte
select d.*
from dbo.TRANSMISIONESDOCUMENTOS d
join tr on
    d.CODIGOCOLEGIO = tr.CodigoColegio
    and d.NUMEROCOLEGIADO = tr.NumeroColegiado
    and d.CODIGODESPACHO = tr.CodigoDespacho
    and d.CODIGOTRANSMISION = tr.CODIGOTRANSMISION
"@ | Out-Null

    Export-Query -Connection $connection -Name "07_EXPEDIENTESLINEAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $expedienteCte
select l.*
from dbo.EXPEDIENTESLineas l
join exp on
    l.CodigoColegio = exp.CodigoColegio
    and l.NumeroColegiado = exp.NumeroColegiado
    and l.CodigoDespacho = exp.CodigoDespacho
    and l.CodigoExpedienteInter = exp.CodigoExpedienteInter
"@ | Out-Null

    Export-Query -Connection $connection -Name "08_EXPEDIENTESDOCUMENTOS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $expedienteCte
select d.*
from dbo.EXPEDIENTESDOCUMENTOS d
join exp on
    d.CODIGOCOLEGIO = exp.CodigoColegio
    and d.NUMEROCOLEGIADO = exp.NumeroColegiado
    and d.CODIGODESPACHO = exp.CodigoDespacho
    and d.CODIGOEXPEDIENTEINTER = exp.CodigoExpedienteInter
"@ | Out-Null

    Export-Query -Connection $connection -Name "09_FACTURASLINEAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $expedienteCte
select fl.*
from dbo.FACTURASLINEAS fl
join exp on
    fl.EXPCODIGOCOLEGIO = exp.CodigoColegio
    and fl.EXPNUMEROCOLEGIADO = exp.NumeroColegiado
    and fl.EXPCODIGODESPACHO = exp.CodigoDespacho
    and fl.EXPCODIGOEXPEDIENTEINTER = exp.CodigoExpedienteInter
"@ | Out-Null

    Export-Query -Connection $connection -Name "10_FACTURAS" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $expedienteCte,
fl as (
    select distinct fl.CODIGOCOLEGIO, fl.NUMEROCOLEGIADO, fl.CODIGODESPACHO, fl.CODIGOFACTURAINTER
    from dbo.FACTURASLINEAS fl
    join exp on
        fl.EXPCODIGOCOLEGIO = exp.CodigoColegio
        and fl.EXPNUMEROCOLEGIADO = exp.NumeroColegiado
        and fl.EXPCODIGODESPACHO = exp.CodigoDespacho
        and fl.EXPCODIGOEXPEDIENTEINTER = exp.CodigoExpedienteInter
)
select f.*
from dbo.FACTURAS f
join fl on
    f.CODIGOCOLEGIO = fl.CODIGOCOLEGIO
    and f.NUMEROCOLEGIADO = fl.NUMEROCOLEGIADO
    and f.CODIGODESPACHO = fl.CODIGODESPACHO
    and f.CODIGOFACTURAINTER = fl.CODIGOFACTURAINTER
"@ | Out-Null

    Export-Query -Connection $connection -Name "11_MATRICULACION" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $vehicleCte
select m.*
from dbo.MATRICULACION m
join v on
    m.VEHCodigoColegio = v.CodigoColegio
    and m.VEHNumeroColegiado = v.NumeroColegiado
    and m.VEHCodigoDespacho = v.CodigoDespacho
    and m.VEHCodigoVehiculo = v.CodigoVehiculo
"@ | Out-Null

    Export-Query -Connection $connection -Name "12_MATRICULACIONATRIB" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $vehicleCte,
mat as (
    select m.*
    from dbo.MATRICULACION m
    join v on
        m.VEHCodigoColegio = v.CodigoColegio
        and m.VEHNumeroColegiado = v.NumeroColegiado
        and m.VEHCodigoDespacho = v.CodigoDespacho
        and m.VEHCodigoVehiculo = v.CodigoVehiculo
)
select a.*
from dbo.MATRICULACIONATRIB a
join mat on
    a.CODIGOCOLEGIO = mat.CodigoColegio
    and a.NUMEROCOLEGIADO = mat.NumeroColegiado
    and a.CODIGODESPACHO = mat.CodigoDespacho
    and a.CODIGOMATRICULACION = mat.CODIGOMATRICULACION
"@ | Out-Null

    Export-Query -Connection $connection -Name "13_IMVTM" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $vehicleCte
select i.*
from dbo.IMVTM i
join v on
    i.VEHCODIGOCOLEGIO = v.CodigoColegio
    and i.VEHNUMEROCOLEGIADO = v.NumeroColegiado
    and i.VEHCODIGODESPACHO = v.CodigoDespacho
    and i.VEHCODIGOVEHICULO = v.CodigoVehiculo
"@ | Out-Null

    Export-Query -Connection $connection -Name "14_ANTECEDENTES" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $vehicleCte
select a.*
from dbo.ANTECEDENTES a
join v on
    a.VEHICODIGOCOLEGIO = v.CodigoColegio
    and a.VEHINUMEROCOLEGIADO = v.NumeroColegiado
    and a.VEHICODIGODESPACHO = v.CodigoDespacho
    and a.VEHICODIGOVEHICULO = v.CodigoVehiculo
"@ | Out-Null

    Export-Query -Connection $connection -Name "15_EXPEDIENTES_POSIBLES_POR_DOCUMENTO" -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters $params -Sql @"
with $transmissionCte
select top ($limit) e.*
from dbo.EXPEDIENTES e
join tr on
    nullif(ltrim(rtrim(tr.NumDocumento)), '') is not null
    and (
        e.IDENTIFICADOR = tr.NumDocumento
        or convert(nvarchar(max), e.DESCRIPCION) like '%' + tr.NumDocumento + '%'
        or convert(nvarchar(max), e.OBSERVACIONES) like '%' + tr.NumDocumento + '%'
    )
"@ | Out-Null

    Export-Valuation620 -Connection $connection -VehicleRows $vehicleRows -TransmissionRows $transmissionRows -OutputDir $OutputDir -SummaryPath $summaryPath

    $catalogQueries = @(
        @{ Name = "90_ATRIBUTOSDEFS"; Sql = "select * from dbo.ATRIBUTOSDEFS" },
        @{ Name = "91_ATRIBUTOSDEFSCLIENTE"; Sql = "select * from dbo.ATRIBUTOSDEFSCLIENTE" },
        @{ Name = "92_CLASIFICACIONDOCUMENTOS"; Sql = "select * from dbo.CLASIFICACIONDOCUMENTOS" },
        @{ Name = "93_SUBCLASIFICACIONDOCUMENTOS"; Sql = "select * from dbo.SUBCLASIFICACIONDOCUMENTOS" },
        @{ Name = "94_DOCUMTIPOS"; Sql = "select * from dbo.DOCUMTIPOS" },
        @{ Name = "95_EXPEDIENTESESTADO"; Sql = "select * from dbo.EXPEDIENTESEstado" },
        @{ Name = "96_TIPOSTRAMITES"; Sql = "select * from dbo.TiposTramites" },
        @{ Name = "97_TRANSMISIONESTIPOS"; Sql = "select * from dbo.TRANSMISIONESTipos" },
        @{ Name = "98_TRANSMISIONESTIPOGEST"; Sql = "select * from dbo.TRANSMISIONESTIPOGEST" },
        @{ Name = "99_TIPOSNOSUJECCIONREDUCCION"; Sql = "select * from dbo.TiposNoSujeccionReduccion" },
        @{ Name = "100_IMPTIPOEEXCEPCIONREDUCCION"; Sql = "select * from dbo.IMPTIPOEEXCEPCIONREDUCCION" },
        @{ Name = "101_VEHICULOS620TIPOVEHICULO"; Sql = "select * from dbo.VEHICULOS620TipoVehiculo" },
        @{ Name = "102_VEHICULOSTRAFTIPOVEHICULO"; Sql = "select * from dbo.VEHICULOSTrafTipoVehiculo" },
        @{ Name = "103_VEHICULOSTRAFTIPOCARBURANTE"; Sql = "select * from dbo.VEHICULOSTrafTipoCarburante" },
        @{ Name = "104_VEHICULOSAEATPROCUSADO"; Sql = "select * from dbo.VEHICULOSAEATProcUsado" },
        @{ Name = "105_TASASTRAFICOTIPO"; Sql = "select * from dbo.TASASTRAFICOTIPO" },
        @{ Name = "106_PROVINCIAS"; Sql = "select * from dbo.Provincias" },
        @{ Name = "107_MUNICIPIOS"; Sql = "select * from dbo.Municipios" }
    )

    foreach ($query in $catalogQueries) {
        Export-Query -Connection $connection -Name $query.Name -OutputDir $OutputDir -SummaryPath $summaryPath -Parameters @{} -Sql $query.Sql | Out-Null
    }

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
