package com.example.gestor_documental.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.example.gestor_documental.config.OcrProperties;
import com.example.gestor_documental.enums.TipoDocumento;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;

class OcrPdfServiceImplClassificationTest {

    private final OcrPdfServiceImpl service = new OcrPdfServiceImpl(new OcrProperties());

    @Test
    void detectaPasaporteComoIdentidad() throws Exception {
        assertEquals(TipoDocumento.DNI, detectar("""
                PASSAPORTO REPUBBLICA ITALIANA
                Cognome Surname ATTANASIO
                Given Names GIACOMO GIUSEPPE
                P<ITAATTANASIO<<GIACOMO<GIUSEPPE
                """));
    }

    @Test
    void detectaCertificadoRegistroNieComoIdentidad() throws Exception {
        assertEquals(TipoDocumento.DNI, detectar("""
                ESPANA CERTIFICADO DE REGISTRO DE CIUDADANO DE LA UNION
                NIE X8987435-R Nacionalidad ITALIA
                Residente comunitario permanente en Espana
                Registro Central de Extranjeros
                """));
    }

    @Test
    void detectaTieComoIdentidad() throws Exception {
        assertEquals(TipoDocumento.DNI, detectar("""
                TARJETA DE IDENTIDAD DE EXTRANJERO
                permiso de residencia y trabajo
                NIE Y1234567-Z Direccion General de la Policia
                """));
    }

    @Test
    void detectaDniConOcrImperfectoDeFoto() throws Exception {
        assertEquals(TipoDocumento.DNI, detectar("""
                REINO DE ESPANA
                DOCUMENTO NACIONAL JENTIDAD
                43332629P
                NATIONAL IDENTITY CARD
                """));
    }

    @Test
    void detectaAutorizacionSerafinAunqueMencionePermisoCirculacion() throws Exception {
        assertEquals(TipoDocumento.AUTORIZACION_SERAFIN, detectar("""
                Modelo de autorizacion
                Hace entrega de la documentacion completa y firmada del vehiculo matricula 9626HMZ
                Permiso de circulacion y ficha tecnica
                """));
    }

    @Test
    void detectaPermisoCirculacionConTextoOcrImperfecto() throws Exception {
        assertEquals(TipoDocumento.PERMISO_CIRCULACION, detectar("""
                3861MPJ SJNFAAF16U1313205 C.1.1 ATTANASIO GIACOMO GIUSEPPE
                P.3 GASOLINA Documento valido si acompana ITV en vigor
                Proxima ITV 26-03-2028 NISSAN JUKE
                """));
    }

    @Test
    void detectaFichaTecnicaPorSenalesItv() throws Exception {
        assertEquals(TipoDocumento.FICHA_TECNICA, detectar("""
                Tarjeta ITV expedida por DGT
                Numero de identificacion Clasificacion del vehiculo Tara maxima
                Neumaticos Codigo NIVE Inspecciones tecnicas
                """));
    }

    @Test
    void detectaPrimeraCaraFichaTecnica() throws Exception {
        assertEquals(TipoDocumento.FICHA_TECNICA, detectar("""
                Matricula 3861MPJ Certificado N 1986731 Codigo Descripcion
                Tarjeta ITV expedida por DGT
                Certifica que el vehiculo cuyas caracteristicas se resenan
                Registro de fabricantes y firmas autorizadas Numero de homologacion
                """));
    }

    @Test
    void detectaFichaTecnicaPorCodigosTecnicos() throws Exception {
        assertEquals(TipoDocumento.FICHA_TECNICA, detectar("""
                A.1 Nombre del fabricante del vehiculo base D.1 Marca D.2 Tipo
                E.1 Numero de identificacion F.1 Masa maxima F.2 MMA
                J.1 Categoria P.1 Cilindrada P.2 Potencia S.1 Numero de plazas V.7 CO2
                """));
    }

    private TipoDocumento detectar(String texto) throws Exception {
        Method method = OcrPdfServiceImpl.class.getDeclaredMethod("detectarTipoDocumento", String.class);
        method.setAccessible(true);
        return (TipoDocumento) method.invoke(service, texto);
    }
}
