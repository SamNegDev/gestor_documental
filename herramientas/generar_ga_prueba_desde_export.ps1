param(
    [string]$ExportDir,
    [string]$TemplatePath = "C:\Users\Sam Neg Dev\Downloads\17062026191553.GA (1).XML",
    [string]$OutputDir = ""
)

$ErrorActionPreference = "Stop"

function Require-File {
    param([string]$Path)
    if (-not (Test-Path -LiteralPath $Path)) {
        throw "No existe el archivo requerido: $Path"
    }
}

function Read-XmlIso {
    param([string]$Path)
    $encoding = [System.Text.Encoding]::GetEncoding("iso-8859-1")
    $text = [System.IO.File]::ReadAllText($Path, $encoding)
    $xml = New-Object System.Xml.XmlDocument
    $xml.PreserveWhitespace = $true
    $xml.LoadXml($text)
    return $xml
}

function Save-XmlIso {
    param(
        [System.Xml.XmlDocument]$Xml,
        [string]$Path
    )
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Encoding = [System.Text.Encoding]::GetEncoding("iso-8859-1")
    $settings.Indent = $true
    $settings.OmitXmlDeclaration = $false
    $writer = [System.Xml.XmlWriter]::Create($Path, $settings)
    try {
        $Xml.Save($writer)
    } finally {
        $writer.Close()
    }
}

function First-Row {
    param([string]$Path)
    Require-File -Path $Path
    $rows = @(Import-Csv -LiteralPath $Path)
    if ($rows.Count -eq 0) {
        return $null
    }
    return $rows[0]
}

function Is-Blank {
    param($Value)
    return [string]::IsNullOrWhiteSpace([string]$Value)
}

function Text-OrEmpty {
    param($Value)
    if ($null -eq $Value) {
        return ""
    }
    return ([string]$Value).Trim()
}

function Try-Decimal {
    param($Value)
    if (Is-Blank $Value) {
        return $null
    }
    $raw = ([string]$Value).Trim().Replace(",", ".")
    $styles = [System.Globalization.NumberStyles]::Float
    $culture = [System.Globalization.CultureInfo]::InvariantCulture
    $number = [decimal]0
    if ([decimal]::TryParse($raw, $styles, $culture, [ref]$number)) {
        return $number
    }
    return $null
}

function Format-DateGa {
    param($Value)
    if (Is-Blank $Value) {
        return ""
    }
    $dt = [datetime]::MinValue
    $cultures = @(
        [System.Globalization.CultureInfo]::GetCultureInfo("es-ES"),
        [System.Globalization.CultureInfo]::InvariantCulture,
        [System.Globalization.CultureInfo]::CurrentCulture
    )
    foreach ($culture in $cultures) {
        if ([datetime]::TryParse([string]$Value, $culture, [System.Globalization.DateTimeStyles]::AssumeLocal, [ref]$dt)) {
            return $dt.ToString("dd/MM/yyyy")
        }
    }
    return [string]$Value
}

function Format-Money11 {
    param($Value)
    $number = Try-Decimal $Value
    if ($null -eq $number) {
        return ""
    }
    $cents = [int64][Math]::Round($number * 100, 0)
    return $cents.ToString("00000000000")
}

function Format-Percent5 {
    param($Value)
    $number = Try-Decimal $Value
    if ($null -eq $number) {
        return ""
    }
    $scaled = [int64][Math]::Round($number * 100, 0)
    return $scaled.ToString("00000")
}

function Depreciated-Value {
    param(
        $FirstYearValue,
        $DepreciationPercent
    )
    $first = Try-Decimal $FirstYearValue
    $percent = Try-Decimal $DepreciationPercent
    if ($null -eq $first -or $null -eq $percent) {
        return $null
    }
    return $first * $percent / 100
}

function Format-Scaled {
    param(
        $Value,
        [int]$Width,
        [int]$Scale = 1
    )
    $number = Try-Decimal $Value
    if ($null -eq $number) {
        return ""
    }
    $scaled = [int64][Math]::Round($number * $Scale, 0)
    return $scaled.ToString(("0" * $Width))
}

function Bool-Ga {
    param($Value, [string]$Default = "No")
    if (Is-Blank $Value) {
        return $Default
    }
    $raw = ([string]$Value).Trim().ToLowerInvariant()
    if ($raw -in @("1", "true", "si", "s", "yes", "y")) {
        return "Si"
    }
    if ($raw -in @("0", "false", "no", "n")) {
        return "No"
    }
    return $Default
}

function Set-Text {
    param(
        [System.Xml.XmlNode]$Base,
        [string]$XPath,
        $Value
    )
    $node = $Base.SelectSingleNode($XPath)
    if ($null -ne $node) {
        $node.InnerText = Text-OrEmpty $Value
    }
}

function Set-AllText {
    param(
        [System.Xml.XmlNode]$Base,
        [string]$XPath,
        $Value
    )
    foreach ($node in @($Base.SelectNodes($XPath))) {
        $node.InnerText = Text-OrEmpty $Value
    }
}

function Find-Person {
    param(
        [object[]]$People,
        $CodigoColegio,
        $NumeroColegiado,
        $CodigoDespacho,
        $CodigoPersona
    )
    if (Is-Blank $CodigoPersona) {
        return $null
    }
    return $People |
        Where-Object {
            $_.CodigoColegio -eq [string]$CodigoColegio -and
            $_.NumeroColegiado -eq [string]$NumeroColegiado -and
            $_.CodigoDespacho -eq [string]$CodigoDespacho -and
            $_.CodigoPersona -eq [string]$CodigoPersona
        } |
        Select-Object -First 1
}

function Attr-Map {
    param(
        [string]$AttrsPath,
        [string]$DefsPath
    )
    $defs = @{}
    foreach ($def in @(Import-Csv -LiteralPath $DefsPath)) {
        $defs[[string]$def.CODIGOATRIBUTODEF] = [string]$def.NOMBRE
    }
    $byCode = @{}
    $byName = @{}
    foreach ($attr in @(Import-Csv -LiteralPath $AttrsPath)) {
        $code = [string]$attr.CODIGOATRIBUTODEF
        $name = $defs[$code]
        $byCode[$code] = [string]$attr.VALOR
        if (-not (Is-Blank $name)) {
            $byName[$name] = [string]$attr.VALOR
        }
    }
    return [pscustomobject]@{
        ByCode = $byCode
        ByName = $byName
    }
}

function Attr-Code {
    param($Attrs, [string]$Code)
    if ($Attrs.ByCode.ContainsKey($Code)) {
        return $Attrs.ByCode[$Code]
    }
    return ""
}

function Attr-Name {
    param($Attrs, [string]$Name)
    if ($Attrs.ByName.ContainsKey($Name)) {
        return $Attrs.ByName[$Name]
    }
    return ""
}

function Fill-PersonBlock {
    param(
        [System.Xml.XmlNode]$TransNode,
        [object]$Person,
        [string]$Suffix
    )
    if ($null -eq $Person) {
        return
    }

    Set-Text $TransNode ".//DNI_$Suffix" $Person.NIF
    Set-Text $TransNode ".//RAZON_SOCIAL_$Suffix" ""
    Set-Text $TransNode ".//APELLIDO1_$Suffix" $Person.Apellido1RazonSocial
    Set-Text $TransNode ".//APELLIDO2_$Suffix" $Person.Apellido2
    Set-Text $TransNode ".//NOMBRE_$Suffix" $Person.Nombre
    Set-Text $TransNode ".//SEXO_$Suffix" $Person.Sexo
    Set-Text $TransNode ".//FECHA_NACIMIENTO_$Suffix" (Format-DateGa $Person.FECHANACIMIENTO)
    Set-Text $TransNode ".//AUTONOMO_$Suffix" (Bool-Ga $Person.AUTONOMOSN)
    Set-Text $TransNode ".//ANAGRAMA_$Suffix" $Person.Anagrama
    Set-Text $TransNode ".//TELEFONO_$Suffix" $Person.Telef
    Set-Text $TransNode ".//FAX_$Suffix" $Person.Fax
    Set-Text $TransNode ".//DOI_SUSTITUTIVO_$Suffix" $Person.TIPODOCUMENTOSUSTITUTIVO
    Set-Text $TransNode ".//FECHA_CADU_DOI_$Suffix" (Format-DateGa $Person.FCADUCNIF)
    Set-Text $TransNode ".//EXENTO_CADU_DOI_$Suffix" "No"
    Set-Text $TransNode ".//NACIONALIDAD_$Suffix" $Person.NACIONALIDAD

    Set-Text $TransNode ".//SIGLAS_DIRECCION_$Suffix" (Normalize-Siglas $Person.DirSiglas)
    Set-Text $TransNode ".//NOMBRE_VIA_DIRECCION_$Suffix" $Person.DIRCALLE
    Set-Text $TransNode ".//NUMERO_DIRECCION_$Suffix" $Person.DIRNUMERO
    Set-Text $TransNode ".//KM_DIRECCION_$Suffix" $Person.DIRKM
    Set-Text $TransNode ".//HECTOMETRO_DIRECCION_$Suffix" $Person.DIRHECTOMETRO
    Set-Text $TransNode ".//LETRA_DIRECCION_$Suffix" $Person.DIRLETRA
    Set-Text $TransNode ".//ESCALERA_DIRECCION_$Suffix" $Person.DIRESC
    Set-Text $TransNode ".//PISO_DIRECCION_$Suffix" $Person.DIRPISO
    Set-Text $TransNode ".//PUERTA_DIRECCION_$Suffix" $Person.DirPuerta
    Set-Text $TransNode ".//BLOQUE_DIRECCION_$Suffix" $Person.DIRBLOQUE
    Set-Text $TransNode ".//MUNICIPIO_$Suffix" (Normalize-Municipio $Person.DirMunicipio $Person.DirProvincia)
    Set-Text $TransNode ".//PUEBLO_$Suffix" $Person.DIRPUEBLO
    Set-Text $TransNode ".//PROVINCIA_$Suffix" (Normalize-Provincia $Person.DirProvincia)
    Set-Text $TransNode ".//CP_$Suffix" $Person.DirCP
    Set-Text $TransNode ".//PAIS_$Suffix" (Normalize-Pais $Person.DIRPAIS)
}

function Normalize-Provincia {
    param($Value)
    if (Is-Blank $Value) {
        return ""
    }
    $raw = ([string]$Value).Trim().ToUpperInvariant()
    if ($raw -in @("TF", "GC")) {
        return $raw
    }
    if ($raw -in @("38", "TENERIFE", "SANTA CRUZ DE TENERIFE")) {
        return "TF"
    }
    if ($raw -in @("35", "LAS PALMAS", "PALMAS")) {
        return "GC"
    }
    return $raw
}

function Normalize-Pais {
    param($Value)
    if (Is-Blank $Value) {
        return "ESP"
    }
    $raw = ([string]$Value).Trim().ToUpperInvariant()
    if ($raw -in @("ES", "ESP", "ESPANA", "ESPAÑA")) {
        return "ESP"
    }
    return $raw
}

function Normalize-Siglas {
    param($Value)
    if (Is-Blank $Value) {
        return ""
    }
    $raw = ([string]$Value).Trim().ToUpperInvariant()
    if ($raw -in @("C", "CL", "CALLE")) {
        return "6"
    }
    return $raw
}

function Normalize-Municipio {
    param($Municipio, $Provincia)
    if (Is-Blank $Municipio) {
        return ""
    }
    $raw = ([string]$Municipio).Trim().ToUpperInvariant()
    $prov = Normalize-Provincia $Provincia
    if ($script:Municipios) {
        $codProv = if ($prov -eq "TF") { "38" } elseif ($prov -eq "GC") { "35" } else { $prov }
        $match = $script:Municipios |
            Where-Object {
                $_.CODPROV -eq $codProv -and (
                    ([string]$_.MUNICIPIO).Trim().ToUpperInvariant() -eq $raw -or
                    ([string]$_.MUNICIPIOINE).Trim().ToUpperInvariant() -eq $raw
                )
            } |
            Select-Object -First 1
        if ($match -and -not (Is-Blank $match.MUNICIPIOINE)) {
            return $match.MUNICIPIOINE
        }
    }
    return $raw
}

function First-CatalogRow {
    param(
        [object[]]$Rows,
        [string]$Column,
        $Value
    )
    if (Is-Blank $Value) {
        return $null
    }
    return $Rows | Where-Object { [string]$_.$Column -eq [string]$Value } | Select-Object -First 1
}

function Map-TipoTasaGa {
    param(
        $GrupoTasa,
        $TipoTasa
    )
    $grupo = [string]$GrupoTasa
    $tipo = [string]$TipoTasa

    # Catalogo interno DGT: 1.5 Vehiculos. En FORMATO_GA los ejemplos oficiales lo exportan como 4.
    if ($grupo -eq "1" -and $tipo -eq "5") {
        return "4"
    }

    # Catalogo interno DGT: 4.1 Notificacion transferencias. En FORMATO_GA se exporta como 6.
    if ($grupo -eq "4" -and $tipo -eq "1") {
        return "6"
    }

    # Catalogo interno DGT: 1.2 Ciclomotores. Los GA de referencia lo exportan como 2.
    if ($grupo -eq "1" -and $tipo -eq "2") {
        return "2"
    }

    return $tipo
}

function Map-SubtipoReleGa {
    param(
        $CodigoTipoTransf,
        $GrupoTasa,
        $TipoTasa
    )
    $tipoTransf = [string]$CodigoTipoTransf
    $grupo = [string]$GrupoTasa
    $tipo = [string]$TipoTasa

    if ($grupo -eq "4" -and $tipo -eq "1") {
        if ($tipoTransf -eq "2") {
            return "SNOTV"
        }
        if ($tipoTransf -eq "4") {
            return "SENTG"
        }
    }

    return "STCCM"
}

function Node-Path {
    param([System.Xml.XmlNode]$Node)
    $parts = New-Object System.Collections.Generic.List[string]
    $current = $Node
    while ($null -ne $current -and $current.NodeType -eq [System.Xml.XmlNodeType]::Element) {
        $parts.Insert(0, $current.Name)
        $current = $current.ParentNode
    }
    return ($parts -join "/")
}

function Leaf-Map {
    param([System.Xml.XmlNode]$Base)
    $map = @{}
    foreach ($node in @($Base.SelectNodes(".//*"))) {
        $hasElementChild = @($node.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element }).Count -gt 0
        if (-not $hasElementChild) {
            $map[(Node-Path $node)] = [string]$node.InnerText
        }
    }
    return $map
}

if (-not $ExportDir) {
    throw "Indica -ExportDir"
}
if (-not (Test-Path -LiteralPath $ExportDir)) {
    throw "No existe ExportDir: $ExportDir"
}
Require-File -Path $TemplatePath

if (-not $OutputDir) {
    $OutputDir = Join-Path (Get-Location) "tmp\ga_pruebas"
}
New-Item -ItemType Directory -Force -Path $OutputDir | Out-Null

$vehicle = First-Row (Join-Path $ExportDir "01_VEHICULOS.csv")
$transmission = First-Row (Join-Path $ExportDir "03_TRANSMISIONES.csv")
$ivtm = First-Row (Join-Path $ExportDir "13_IMVTM.csv")
$valuationPath = Join-Path $ExportDir "17_VALORACION_SELECCIONADA_620.csv"
$valuation = if (Test-Path -LiteralPath $valuationPath) { First-Row $valuationPath } else { $null }
$people = @(Import-Csv -LiteralPath (Join-Path $ExportDir "02_PERSONAS_RELACIONADAS.csv"))
$attrs = Attr-Map -AttrsPath (Join-Path $ExportDir "05_TRANSMISIONATRIB.csv") -DefsPath (Join-Path $ExportDir "90_ATRIBUTOSDEFS.csv")
$script:Municipios = @(Import-Csv -LiteralPath (Join-Path $ExportDir "107_MUNICIPIOS.csv"))
$tipos620 = @(Import-Csv -LiteralPath (Join-Path $ExportDir "101_VEHICULOS620TIPOVEHICULO.csv"))

if ($null -eq $vehicle -or $null -eq $transmission) {
    throw "Faltan vehiculo o transmision en la exportacion"
}

$official = Read-XmlIso -Path $TemplatePath
$candidate = Read-XmlIso -Path $TemplatePath
$transNode = $candidate.SelectSingleNode("//TRANSMISION")
if ($null -eq $transNode) {
    throw "La plantilla no contiene TRANSMISION"
}

foreach ($leaf in @($transNode.SelectNodes(".//*") | Where-Object { -not ($_.ChildNodes | Where-Object { $_.NodeType -eq [System.Xml.XmlNodeType]::Element }) })) {
    $leaf.InnerText = ""
}

Set-AllText $transNode ".//*[starts-with(local-name(), 'EXENTO_CADU_DOI_')]" "Si"

$adq = Find-Person $people $transmission.ADQCodigoColegio $transmission.ADQNumeroColegiado $transmission.ADQCodigoDespacho $transmission.ADQCodigoPersona
$trans = Find-Person $people $transmission.TRANSCodigoColegio $transmission.TRANSNumeroColegiado $transmission.TRANSCodigoDespacho $transmission.TRANSCodigoPersona
$pre = Find-Person $people $transmission.PRECodigoColegio $transmission.PRENumeroColegiado $transmission.PRECodigoDespacho $transmission.PRECodigoPersona

Set-Text $transNode "./TIPO_TRANSFERENCIA" $transmission.CodigoTipoTransf
Set-Text $transNode "./NOTIFICACION_PREVIA" "No"
Set-Text $transNode "./NUMERO_DOCUMENTO" $transmission.NumDocumento
Set-Text $transNode "./NUMERO_PROFESIONAL" "00387"
Set-Text $transNode "./FECHA_CREACION" (Get-Date -Format "dd/MM/yyyy")
Set-Text $transNode "./FECHA_PRESENTACION" (Get-Date -Format "dd/MM/yyyy")
Set-Text $transNode "./JEFATURA" "TF"
Set-Text $transNode "./TIPO_TASA" (Map-TipoTasaGa $transmission.TIPOGRUPOTASA $transmission.TIPOTIPOTASA)
Set-Text $transNode "./TASA" ""
Set-Text $transNode "./OBSERVACIONES" $transmission.Observaciones
Set-Text $transNode "./IMPRESION_PERMISO_CIRCULACION" (Bool-Ga (Attr-Code $attrs "36"))
Set-Text $transNode "./SUBTIPO_RELE" (Map-SubtipoReleGa $transmission.CodigoTipoTransf $transmission.TIPOGRUPOTASA $transmission.TIPOTIPOTASA)
Set-Text $transNode "./SEPARACION_DIVORCIO" (Bool-Ga (Attr-Code $attrs "162"))

Fill-PersonBlock -TransNode $transNode -Person $trans -Suffix "TRANSMITENTE"
Fill-PersonBlock -TransNode $transNode -Person $adq -Suffix "ADQUIRENTE"
Fill-PersonBlock -TransNode $transNode -Person $pre -Suffix "PRESENTADOR"
if ($null -ne $trans) {
    Set-Text $transNode ".//ANAGRAMA_TRANS" $trans.Anagrama
}

Set-AllText $transNode ".//ESCOMPRAVENTA" (Bool-Ga (Attr-Code $attrs "90"))
Set-Text $transNode ".//COTITULARES_TRANSMITENTE" $(if (Is-Blank $transmission.NUMCOTITULARESTRANS) { "0" } else { $transmission.NUMCOTITULARESTRANS })
Set-Text $transNode ".//COTITULARES_ADQUIRENTE" $(if (Is-Blank $transmission.NUMCOTITULARESADQ) { "0" } else { $transmission.NUMCOTITULARESADQ })
Set-Text $transNode ".//CAMBIO_DOMICILIO_ADQUIRENTE" "No"
Set-Text $transNode ".//MANDATARIO_PRESENTADOR" "No"
Set-Text $transNode ".//INTERESADO_PRESENTADOR" "No"

Set-Text $transNode ".//MATRICULA" $vehicle.Matricula
$fechaReferenciaMatriculacion = if (Is-Blank $vehicle.F1MATRICULACION) { Format-DateGa $vehicle.FMATRICULACION } else { Format-DateGa $vehicle.F1MATRICULACION }
Set-Text $transNode ".//FECHA_MATRICULACION" $fechaReferenciaMatriculacion
Set-Text $transNode ".//FECHA_PRIMERA_MATRICULACION" $fechaReferenciaMatriculacion
Set-Text $transNode ".//PROVINCIA_PRIMERA_MATRICULACION" $(if (Is-Blank $vehicle.PROVF1MATRI) { "ND" } else { $vehicle.PROVF1MATRI })
Set-Text $transNode ".//MARCA" $vehicle.FTMarca
Set-Text $transNode ".//MODELO" $(if (Is-Blank $vehicle.FTMODELOMATR) { $vehicle.FTMODELOTRANS } else { $vehicle.FTMODELOMATR })
Set-Text $transNode ".//NUMERO_BASTIDOR" $vehicle.FTBastidor
Set-Text $transNode ".//CILINDRADA" (Format-Scaled $vehicle.FTCilindrada 7 100)
Set-Text $transNode ".//POTENCIA" (Format-Scaled $vehicle.FTPotencia 5 100)
Set-Text $transNode ".//CARBURANTE" $vehicle.FTCarburante
Set-Text $transNode ".//NUMERO_CILINDROS" (Format-Scaled $vehicle.FTNCILINDROS 2 1)
Set-Text $transNode ".//MASA" (Format-Scaled $vehicle.FTMasa 6 1)
Set-Text $transNode ".//TARA" (Format-Scaled $vehicle.FTTara 6 1)
Set-Text $transNode ".//PLAZAS" (Format-Scaled $vehicle.FTPLAZAS 3 1)
Set-Text $transNode ".//MODO_ADJUDICACION" $transmission.CodigoModoAdj
Set-Text $transNode ".//SERVICIO_DESTINA" (Attr-Code $attrs "23")
Set-Text $transNode ".//CAMBIO_SERVICIO" (Bool-Ga (Attr-Code $attrs "45"))
$tipo620 = First-CatalogRow $tipos620 "CODIGO620TIPOVEHICULO" $vehicle.Codigo620TipoVehiculo
Set-Text $transNode ".//CLASE_VEHICULO" $(if ($tipo620) { $tipo620.TT } else { "" })
Set-Text $transNode ".//TIPO_VEHICULO" $(if ([string]$vehicle.Codigo620TipoVehiculo -eq "3") { "50" } else { $vehicle.CodigoTrafTipoVehiculo })
Set-Text $transNode ".//CODIGO_ITV_INDUSTRIA" $(if (Is-Blank $vehicle.FTCODIGOITV) { $vehicle.FTClasificacionVeh } else { $vehicle.FTCODIGOITV })
Set-Text $transNode ".//MOTIVO_ITV" $transmission.CodigoMotivoITV
Set-Text $transNode ".//FECHA_ITV" (Format-DateGa $vehicle.FITV)
Set-Text $transNode ".//RENTING" (Bool-Ga $vehicle.RENTING)
Set-Text $transNode ".//HISTORICO" (Bool-Ga $vehicle.HISTORICOSN)
Set-Text $transNode ".//CARSHARING" (Bool-Ga $vehicle.CARSHARINGSN)
Set-Text $transNode ".//VELOCIDAD" (Format-Scaled $vehicle.FTVELOCIDADMAX 3 1)

$yearTag = "A$([char]0x00D1)O_FABRICACION"
$anyoFabricacion = if ($fechaReferenciaMatriculacion -match "\d{2}/\d{2}/(\d{4})") { $Matches[1] } else { $vehicle.AnoFabricacionVeh }
Set-Text $transNode ".//*[local-name()='$yearTag']" $anyoFabricacion

Set-Text $transNode ".//CAVALORACION" "GC"
Set-Text $transNode ".//ANYOVALORACION" $(if (Is-Blank $transmission.IMP620ANYOVALORACION) { (Get-Date).Year } else { $transmission.IMP620ANYOVALORACION })
Set-Text $transNode ".//ID_AUTOLIQUIDACION" "No"
Set-Text $transNode ".//FECHA_DEVENGO" (Format-DateGa $transmission.IMP620FDEVENGO)
Set-Text $transNode ".//IMPUESTO_EXENTO" (Bool-Ga (Attr-Code $attrs "58"))
Set-Text $transNode ".//IMPUESTO_NO_SUJETO" (Bool-Ga (Attr-Code $attrs "62"))
Set-Text $transNode ".//REDUCCION" (Format-Percent5 $transmission.IMP620PORCENTAJEREDUCCION)
$firstYearFiscalValue = if ($valuation -and -not (Is-Blank $valuation.VALORFISCAL1ER)) { $valuation.VALORFISCAL1ER } else { $transmission.IMP620VALORFISCAL1ANYO }
$depreciatedFiscalValue = if ($valuation -and -not (Is-Blank $valuation.VALORFISCAL1ER)) { Depreciated-Value $valuation.VALORFISCAL1ER $transmission.IMP620VALORACION } else { $transmission.IMP620VALORFISCAL }
Set-Text $transNode ".//*[local-name()='VALOR_FISCAL_PRIMER_A$([char]0x00D1)O']" (Format-Money11 $firstYearFiscalValue)
Set-Text $transNode ".//PORCENTAJE_DEPRECIACION" (Format-Percent5 $transmission.IMP620VALORACION)
Set-Text $transNode ".//VALOR_FISCAL" (Format-Money11 $depreciatedFiscalValue)
Set-Text $transNode ".//BASE_IMPONIBLE" (Format-Money11 $transmission.IMP620BASEIMPONIBLE)
Set-Text $transNode ".//PORCENTAJE_ADQUISICION" (Format-Percent5 $transmission.IMP620PORCENTAJEADQ)
Set-Text $transNode ".//TIPO_GRAVAMEN" (Format-Percent5 $transmission.IMP620TIPOIMPOSITIVO)
Set-Text $transNode ".//CUOTA_TRIBUTARIA" (Format-Money11 $transmission.IMP620CUOTATRIBUTARIA)
Set-Text $transNode ".//RECARGO" (Format-Money11 $transmission.IMP620RECARGO)
Set-Text $transNode ".//INTERESES_DEMORA" (Format-Money11 $transmission.IMP620INTERESDEMORA)
Set-Text $transNode ".//TOTAL_INGRESAR" (Format-Money11 $transmission.IMP620TOTALINGRESAR)
Set-Text $transNode ".//IMPORTE_INGRESADO" $(if (Is-Blank $transmission.IMP620NRC) { "00000000000" } else { Format-Money11 $transmission.IMP620NRCIMPORTE })
Set-Text $transNode ".//COMPLEMENTARIA" (Bool-Ga $transmission.IMP620COMPLEAUTOLIQ)
Set-Text $transNode ".//BASE_LIQUIDABLE" (Format-Money11 $transmission.IMP620BASELIQUIDABLE)
Set-Text $transNode ".//INGRESAR" (Format-Money11 $transmission.IMP620TOTALINGRESAR)
Set-Text $transNode ".//VALOR_DECLARADO" (Format-Money11 $transmission.IMP620VALORDECLARADO)
Set-Text $transNode ".//SUJETO_IGIC" "No"
Set-Text $transNode ".//VENDEDOR_HABITUAL" "No"
Set-Text $transNode ".//FORMA_PAGO" $(if (Is-Blank $transmission.IMP620CODIGOFORMAPAGO) { "I" } else { $transmission.IMP620CODIGOFORMAPAGO })
Set-Text $transNode ".//DNI_TITULAR_CUENTA" $transmission.IMP620NIFTITULARCCC
Set-Text $transNode ".//NOMBRE_TITULAR_CUENTA" $transmission.IMP620TITULARCCCTARJETA
Set-Text $transNode ".//NOMBRE_TITULAR_TARJETA" $transmission.IMP620TITULARCCCTARJETA
Set-Text $transNode ".//CODIGO_ELECTRONICO_TRANSFERENCIA" ""

Set-Text $transNode ".//SOLICITUD" (Bool-Ga (Attr-Code $attrs "48"))
Set-Text $transNode ".//CONSENTIMIENTO" "N/A"
Set-Text $transNode ".//CONTRATO_COMPRAVENTA" (Bool-Ga (Attr-Code $attrs "50"))
Set-Text $transNode ".//IVA" (Bool-Ga (Attr-Code $attrs "52"))
Set-Text $transNode ".//ACTA_ADJUDICACION_SUBASTA" (Bool-Ga (Attr-Code $attrs "53"))
Set-Text $transNode ".//SENTENCIA_JUDICIAL_ADJUDICACION" (Bool-Ga (Attr-Code $attrs "54"))
Set-Text $transNode ".//ACTA_NOTARIAL" (Bool-Ga (Attr-Code $attrs "163"))
Set-Text $transNode ".//ACREDITACION_POSESION" (Bool-Ga (Attr-Code $attrs "55"))
Set-Text $transNode ".//ACREDITACION_HERENCIA" (Bool-Ga (Attr-Code $attrs "56"))

Set-Text $transNode ".//MODELO_ITP" (Attr-Code $attrs "57")
Set-Text $transNode ".//EXENCION_ITP" (Bool-Ga (Attr-Code $attrs "58"))
Set-Text $transNode ".//NO_SUJECION_ITP" (Bool-Ga (Attr-Code $attrs "62"))
Set-Text $transNode ".//PROVINCIACET" ""
Set-Text $transNode ".//CET_ITP" ""
Set-Text $transNode ".//NO_OBLIGADO_ITP" "No"
Set-Text $transNode ".//EXENCION_ISD" "No"
Set-Text $transNode ".//NO_SUJECION_ISD" "No"
Set-Text $transNode ".//EXENCION_IEDMT" "No"
Set-Text $transNode ".//NO_SUJECION_IEDMT" "No"
Set-Text $transNode ".//DUA" (Bool-Ga (Attr-Code $attrs "66"))
Set-Text $transNode ".//ALTA_IVTM" (Bool-Ga (Attr-Code $attrs "67"))
Set-Text $transNode ".//VEHICULOS_AGRICOLAS" (Bool-Ga (Attr-Code $attrs "68"))
Set-Text $transNode ".//VEHICULOS_TRANSPORTE" (Bool-Ga (Attr-Code $attrs "87"))

$matricula = if (Is-Blank $vehicle.Matricula) { "SIN_MATRICULA" } else { ([string]$vehicle.Matricula).Trim().ToUpperInvariant() }
$candidatePath = Join-Path $OutputDir "FORMATO_GA_${matricula}_generado_db.xml"
$reportPath = Join-Path $OutputDir "FORMATO_GA_${matricula}_comparacion_sin_valores.csv"
$summaryPath = Join-Path $OutputDir "FORMATO_GA_${matricula}_resumen.txt"

Save-XmlIso -Xml $candidate -Path $candidatePath

$officialMap = Leaf-Map $official.SelectSingleNode("//TRANSMISION")
$candidateMap = Leaf-Map $candidate.SelectSingleNode("//TRANSMISION")
$rows = New-Object System.Collections.Generic.List[object]
foreach ($path in ($officialMap.Keys | Sort-Object)) {
    $officialValue = [string]$officialMap[$path]
    $candidateValue = if ($candidateMap.ContainsKey($path)) { [string]$candidateMap[$path] } else { "" }
    $officialHasValue = -not [string]::IsNullOrWhiteSpace($officialValue)
    $candidateHasValue = -not [string]::IsNullOrWhiteSpace($candidateValue)
    $equals = $officialValue -eq $candidateValue
    $category = if ($equals) {
        "igual"
    } elseif ($officialHasValue -and -not $candidateHasValue) {
        "falta_en_generado"
    } elseif (-not $officialHasValue -and $candidateHasValue) {
        "relleno_extra"
    } else {
        "distinto"
    }
    $rows.Add([pscustomobject]@{
        Path = $path
        Tag = ($path -split "/")[-1]
        OficialTieneValor = $officialHasValue
        GeneradoTieneValor = $candidateHasValue
        Igual = $equals
        Categoria = $category
    })
}

$rows | Export-Csv -LiteralPath $reportPath -NoTypeInformation -Encoding UTF8

$total = $rows.Count
$officialNonEmpty = @($rows | Where-Object OficialTieneValor).Count
$candidateNonEmpty = @($rows | Where-Object GeneradoTieneValor).Count
$equal = @($rows | Where-Object Igual).Count
$missing = @($rows | Where-Object { $_.Categoria -eq "falta_en_generado" }).Count
$different = @($rows | Where-Object { $_.Categoria -eq "distinto" }).Count
$extra = @($rows | Where-Object { $_.Categoria -eq "relleno_extra" }).Count

@(
    "Prueba FORMATO_GA desde exportacion SQL",
    "Fecha: $(Get-Date -Format s)",
    "ExportDir: $ExportDir",
    "TemplatePath: $TemplatePath",
    "CandidatePath: $candidatePath",
    "ReportPath: $reportPath",
    "",
    "Total campos hoja TRANSMISION: $total",
    "Campos con valor en XML oficial: $officialNonEmpty",
    "Campos con valor en XML generado: $candidateNonEmpty",
    "Campos iguales al oficial: $equal",
    "Campos oficiales con valor que faltan en generado: $missing",
    "Campos con valor distinto: $different",
    "Campos rellenos extra: $extra"
) | Set-Content -LiteralPath $summaryPath -Encoding UTF8

Write-Host ""
Write-Host "PRUEBA GENERADA"
Write-Host "XML:     $candidatePath"
Write-Host "Informe: $reportPath"
Write-Host "Resumen: $summaryPath"
Write-Host ""
Write-Host "Campos con valor en generado: $candidateNonEmpty / $total"
Write-Host "Faltan respecto al oficial: $missing"
Write-Host "Distintos respecto al oficial: $different"
