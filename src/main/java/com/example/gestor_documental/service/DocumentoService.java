package com.example.gestor_documental.service;

import org.springframework.context.annotation.Bean;
import org.springframework.web.multipart.MultipartFile;



public interface DocumentoService {

    void guardar(Long expedienteId, MultipartFile multipartFile);

}
