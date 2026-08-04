package com.example.gestor_documental.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.gestor_documental.dto.auditoria.AuditoriaDocumentoContext;
import com.example.gestor_documental.enums.AccionAuditoriaDocumento;
import com.example.gestor_documental.enums.ResultadoAuditoriaDocumento;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.enums.TipoDocumento;
import com.example.gestor_documental.model.AuditoriaDocumento;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.Documento;
import com.example.gestor_documental.model.Expediente;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.AuditoriaDocumentoRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;

@ExtendWith(MockitoExtension.class)
class AuditoriaDocumentoServiceImplTest {

    @Mock AuditoriaDocumentoRepository repository;
    @Mock PlatformTransactionManager transactionManager;
    @Mock HttpServletRequest request;

    private AuditoriaDocumentoServiceImpl service;

    @BeforeEach
    void preparar() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class)))
                .thenReturn(new SimpleTransactionStatus());
        service = new AuditoriaDocumentoServiceImpl(repository, transactionManager);
    }

    @Test
    void conservaUnaInstantaneaDelDocumentoYElUsuario() {
        Cliente cliente = new Cliente();
        cliente.setId(7L);
        Expediente expediente = new Expediente();
        expediente.setId(19L);
        expediente.setCliente(cliente);
        Documento documento = new Documento();
        documento.setId(31L);
        documento.setNombreArchivoOriginal("permiso-circulacion.pdf");
        documento.setTipoDocumento(TipoDocumento.PERMISO_CIRCULACION);
        documento.setExpediente(expediente);
        Usuario usuario = new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true);
        usuario.setId(5L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Navegador de prueba");

        AuditoriaDocumentoContext contexto = service.crearContexto(documento, usuario, request);
        service.registrar(contexto, AccionAuditoriaDocumento.DESCARGAR,
                ResultadoAuditoriaDocumento.CORRECTO, null);

        ArgumentCaptor<AuditoriaDocumento> captor = ArgumentCaptor.forClass(AuditoriaDocumento.class);
        verify(repository).save(captor.capture());
        AuditoriaDocumento evento = captor.getValue();
        assertThat(evento.getRecursoTipo()).isEqualTo("DOCUMENTO");
        assertThat(evento.getRecursoId()).isEqualTo(31L);
        assertThat(evento.getDocumentoId()).isEqualTo(31L);
        assertThat(evento.getDocumentoNombre()).isEqualTo("permiso-circulacion.pdf");
        assertThat(evento.getDocumentoTipo()).isEqualTo("PERMISO_CIRCULACION");
        assertThat(evento.getExpedienteId()).isEqualTo(19L);
        assertThat(evento.getClienteId()).isEqualTo(7L);
        assertThat(evento.getUsuarioId()).isEqualTo(5L);
        assertThat(evento.getAccion()).isEqualTo(AccionAuditoriaDocumento.DESCARGAR);
        assertThat(evento.getResultado()).isEqualTo(ResultadoAuditoriaDocumento.CORRECTO);
    }

    @Test
    void unFalloDeAuditoriaNoRompeLaOperacionDocumental() {
        when(repository.save(any(AuditoriaDocumento.class)))
                .thenThrow(new DataAccessResourceFailureException("base no disponible"));
        AuditoriaDocumentoContext contexto = new AuditoriaDocumentoContext(
                31L, "documento.pdf", "OTRO", 19L, null, 7L,
                5L, "ada@example.test", "ADMIN", "127.0.0.1", "test");

        assertThatCode(() -> service.registrar(
                contexto,
                AccionAuditoriaDocumento.VISUALIZAR,
                ResultadoAuditoriaDocumento.CORRECTO,
                null)).doesNotThrowAnyException();
    }
    @Test
    void conservaElContextoGenericoDeUnaExportacion() {
        Usuario usuario = new Usuario("Ada", "Lovelace", "ada@example.test", "secret", RolUsuario.ADMIN, true);
        usuario.setId(5L);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");
        when(request.getHeader("User-Agent")).thenReturn("Navegador de prueba");
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/api/expedientes/19/historial/exportar");

        AuditoriaDocumentoContext contexto = service.crearContextoEvento(
                "EXPEDIENTE", 19L, "EXP-19", 19L, null, 7L, usuario, request);
        service.registrar(contexto, AccionAuditoriaDocumento.EXPORTAR_HISTORIAL,
                ResultadoAuditoriaDocumento.CORRECTO, "Categoria: ESTADO");

        ArgumentCaptor<AuditoriaDocumento> captor = ArgumentCaptor.forClass(AuditoriaDocumento.class);
        verify(repository).save(captor.capture());
        AuditoriaDocumento evento = captor.getValue();
        assertThat(evento.getRecursoTipo()).isEqualTo("EXPEDIENTE");
        assertThat(evento.getRecursoId()).isEqualTo(19L);
        assertThat(evento.getExpedienteId()).isEqualTo(19L);
        assertThat(evento.getMetodoHttp()).isEqualTo("GET");
        assertThat(evento.getRuta()).isEqualTo("/api/expedientes/19/historial/exportar");
        assertThat(evento.getDetalle()).isEqualTo("Categoria: ESTADO");
    }
}
