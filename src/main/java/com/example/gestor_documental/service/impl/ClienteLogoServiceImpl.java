package com.example.gestor_documental.service.impl;

import com.example.gestor_documental.enums.TipoLogoCliente;
import com.example.gestor_documental.model.Cliente;
import com.example.gestor_documental.repository.ClienteRepository;
import com.example.gestor_documental.service.ClienteLogoService;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ClienteLogoServiceImpl implements ClienteLogoService {

    private static final long MAX_FILE_SIZE = 5L * 1024L * 1024L;
    private static final int MIN_DIMENSION = 64;
    private static final int MAX_DIMENSION = 4096;

    private final ClienteRepository clienteRepository;

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Override
    @Transactional
    public Cliente guardar(Long clienteId, TipoLogoCliente tipo, MultipartFile archivo) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        ImageInfo info = validar(archivo);
        Path directory = root().resolve("clientes").resolve(String.valueOf(clienteId)).resolve("branding");
        Path destination = directory.resolve(tipo.routeValue() + "-" + UUID.randomUUID() + "." + info.extension());
        String anterior = path(cliente, tipo);

        try {
            Files.createDirectories(directory);
            try (InputStream input = archivo.getInputStream()) {
                Files.copy(input, destination, StandardCopyOption.REPLACE_EXISTING);
            }
            setPath(cliente, tipo, root().relativize(destination).toString());
            Cliente guardado = clienteRepository.save(cliente);
            eliminarPath(anterior);
            return guardado;
        } catch (IOException exception) {
            eliminarSilencioso(destination);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar el logo", exception);
        }
    }

    @Override
    @Transactional
    public Cliente eliminar(Long clienteId, TipoLogoCliente tipo) {
        Cliente cliente = clienteRepository.findById(clienteId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cliente no encontrado"));
        String anterior = path(cliente, tipo);
        setPath(cliente, tipo, null);
        Cliente guardado = clienteRepository.save(cliente);
        eliminarPath(anterior);
        return guardado;
    }

    @Override
    public Path resolver(Cliente cliente, TipoLogoCliente tipo) {
        String storedPath = path(cliente, tipo);
        if (storedPath == null || storedPath.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo no configurado");
        }
        Path resolved = root().resolve(storedPath).normalize();
        if (!resolved.startsWith(root()) || !Files.isRegularFile(resolved)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Logo no encontrado");
        }
        return resolved;
    }

    @Override
    public void eliminarArchivos(Cliente cliente) {
        eliminarPath(cliente.getLogoPrincipalPath());
        eliminarPath(cliente.getLogoCompactoPath());
    }

    private ImageInfo validar(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Selecciona una imagen");
        }
        if (archivo.getSize() > MAX_FILE_SIZE) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "El logo no puede superar 5 MB");
        }

        try (InputStream input = archivo.getInputStream(); ImageInputStream imageInput = ImageIO.createImageInputStream(input)) {
            if (imageInput == null) {
                throw invalidImage();
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInput);
            if (!readers.hasNext()) {
                throw invalidImage();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInput, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                String extension = switch (format) {
                    case "png" -> "png";
                    case "jpeg", "jpg" -> "jpg";
                    default -> throw invalidImage();
                };
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_DIMENSION || height < MIN_DIMENSION
                        || width > MAX_DIMENSION || height > MAX_DIMENSION) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "La imagen debe medir entre 64 y 4096 px por lado");
                }
                return new ImageInfo(extension, width, height);
            } finally {
                reader.dispose();
            }
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (IOException exception) {
            throw invalidImage();
        }
    }

    private ResponseStatusException invalidImage() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo debe ser una imagen PNG o JPEG valida");
    }

    private Path root() {
        return Path.of(uploadDir).toAbsolutePath().normalize();
    }

    private String path(Cliente cliente, TipoLogoCliente tipo) {
        return tipo == TipoLogoCliente.PRINCIPAL ? cliente.getLogoPrincipalPath() : cliente.getLogoCompactoPath();
    }

    private void setPath(Cliente cliente, TipoLogoCliente tipo, String value) {
        if (tipo == TipoLogoCliente.PRINCIPAL) {
            cliente.setLogoPrincipalPath(value);
        } else {
            cliente.setLogoCompactoPath(value);
        }
    }

    private void eliminarPath(String storedPath) {
        if (storedPath == null || storedPath.isBlank()) {
            return;
        }
        Path resolved = root().resolve(storedPath).normalize();
        if (resolved.startsWith(root())) {
            eliminarSilencioso(resolved);
        }
    }

    private void eliminarSilencioso(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // La referencia activa ya se ha actualizado; el archivo huerfano puede limpiarse posteriormente.
        }
    }

    private record ImageInfo(String extension, int width, int height) {
    }
}
