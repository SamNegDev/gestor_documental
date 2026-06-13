package com.example.gestor_documental.service;

import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.model.Cliente;
import java.nio.file.Path;
import org.springframework.web.multipart.MultipartFile;

public interface ClienteLogoService {

    Cliente guardar(Long clienteId, TipoLogoCliente tipo, MultipartFile archivo);

    Cliente eliminar(Long clienteId, TipoLogoCliente tipo);

    Path resolver(Cliente cliente, TipoLogoCliente tipo);

    void eliminarArchivos(Cliente cliente);
}
