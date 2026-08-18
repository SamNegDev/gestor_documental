package com.example.gestor_documental.controller.api;

import com.example.gestor_documental.dto.expediente.AdministradorClienteRequest;
import com.example.gestor_documental.enums.RolUsuario;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.model.ClienteInteresado;
import com.example.gestor_documental.model.Interesado;
import com.example.gestor_documental.model.Usuario;
import com.example.gestor_documental.repository.ClienteInteresadoRepository;
import com.example.gestor_documental.repository.InteresadoRepository;
import com.example.gestor_documental.security.CurrentUserService;
import com.example.gestor_documental.service.ClienteLogoService;
import com.example.gestor_documental.service.ClienteService;
import com.example.gestor_documental.service.DocumentoService;
import com.example.gestor_documental.service.UsuarioService;
import com.example.gestor_documental.service.WhatsappOutboundService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminManagementApiControllerRepresentanteTest {

    @Mock ClienteService clienteService;
    @Mock ClienteLogoService clienteLogoService;
    @Mock DocumentoService documentoService;
    @Mock UsuarioService usuarioService;
    @Mock CurrentUserService currentUserService;
    @Mock WhatsappOutboundService whatsappOutboundService;
    @Mock ClienteInteresadoRepository clienteInteresadoRepository;
    @Mock InteresadoRepository interesadoRepository;
    @Mock Authentication authentication;
    @Mock HttpServletRequest servletRequest;

    private AdminManagementApiController controller;

    @BeforeEach
    void setUp() {
        controller = new AdminManagementApiController(
                clienteService,
                clienteLogoService,
                documentoService,
                usuarioService,
                currentUserService,
                whatsappOutboundService,
                clienteInteresadoRepository,
                interesadoRepository);
        when(currentUserService.requireAdmin(authentication))
                .thenReturn(new Usuario("Admin", "Test", "admin@test.local", "secret", RolUsuario.ADMIN, true));
    }

    @Test
    void asignaUnRepresentanteValidoComoHabitual() {
        Cliente cliente = empresa();
        when(clienteService.buscarPorId(4L)).thenReturn(Optional.of(cliente));
        when(interesadoRepository.findByIdentificadorNormalizado(any(), any())).thenReturn(List.of());
        when(interesadoRepository.save(any(Interesado.class))).thenAnswer(invocation -> {
            Interesado interesado = invocation.getArgument(0);
            interesado.setId(637L);
            return interesado;
        });
        when(clienteInteresadoRepository.findByClienteIdAndInteresadoId(4L, 637L)).thenReturn(Optional.empty());
        when(documentoService.listarPorCliente(4L)).thenReturn(List.of());
        when(clienteInteresadoRepository.findByClienteIdAndRepresentanteLegalTrueOrderByInteresadoNombreAsc(4L))
                .thenReturn(List.of());

        controller.guardarAdministrador(4L, request("12.345.678-Z", "Antonio Armas"), authentication, servletRequest);

        ArgumentCaptor<ClienteInteresado> relacionCaptor = ArgumentCaptor.forClass(ClienteInteresado.class);
        verify(clienteInteresadoRepository).save(relacionCaptor.capture());
        assertThat(relacionCaptor.getValue().getRepresentanteLegal()).isTrue();
        assertThat(relacionCaptor.getValue().getHabitual()).isTrue();
        assertThat(relacionCaptor.getValue().getInteresado().getDni()).isEqualTo("12345678Z");
    }

    @Test
    void impideAsignarRepresentantesAUnClienteQueNoEsEmpresa() {
        Cliente cliente = empresa();
        cliente.setNif("12345678Z");
        when(clienteService.buscarPorId(4L)).thenReturn(Optional.of(cliente));

        assertThatThrownBy(() -> controller.guardarAdministrador(
                4L, request("12345678Z", "Antonio Armas"), authentication, servletRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("clientes empresa");
    }

    @Test
    void noSobrescribeAUnaPersonaExistenteConOtroNombre() {
        Cliente cliente = empresa();
        Interesado existente = new Interesado("12345678Z", "MARIA LOPEZ");
        existente.setId(637L);
        when(clienteService.buscarPorId(4L)).thenReturn(Optional.of(cliente));
        when(interesadoRepository.findByIdentificadorNormalizado(any(), any())).thenReturn(List.of(existente));

        assertThatThrownBy(() -> controller.guardarAdministrador(
                4L, request("12345678Z", "Antonio Armas"), authentication, servletRequest))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("ya pertenece a MARIA LOPEZ");
    }

    @Test
    void explicaQueUsuariosImpidenEliminarUnCliente() {
        Cliente cliente = empresa();
        Usuario usuario = new Usuario("Nico", "G", "nico@test.com", "secret", RolUsuario.CLIENTE, true);
        when(clienteService.buscarPorId(4L)).thenReturn(Optional.of(cliente));
        when(usuarioService.listarAsociadosAlCliente(4L)).thenReturn(List.of(usuario));

        assertThatThrownBy(() -> controller.eliminarCliente(4L, authentication))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Nico G")
                .hasMessageContaining("nico@test.com");
        verify(clienteService, never()).eliminar(anyLong());
    }

    private Cliente empresa() {
        Cliente cliente = new Cliente();
        cliente.setId(4L);
        cliente.setNif("B38436556");
        cliente.setNombre("CANARIOALEMANA DE AUTOMOVILES");
        cliente.setEmail("cliente@example.com");
        return cliente;
    }

    private AdministradorClienteRequest request(String dni, String nombre) {
        return new AdministradorClienteRequest(
                dni, nombre, null, null,
                null, null, null, null,
                null, null, null, null,
                null, null, null, null);
    }
}
