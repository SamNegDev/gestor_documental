package com.example.gestor_documental.dto;

import com.example.gestor_documental.enums.TipoDocumento;
import org.springframework.web.multipart.MultipartFile;

    public class DocumentoFormDto {

        private MultipartFile archivo;
        private TipoDocumento tipoDocumento;

        public MultipartFile getArchivo() {
            return archivo;
        }

        public void setArchivo(MultipartFile archivo) {
            this.archivo = archivo;
        }

        public TipoDocumento getTipoDocumento() {
            return tipoDocumento;
        }

        public void setTipoDocumento(TipoDocumento tipoDocumento) {
            this.tipoDocumento = tipoDocumento;
        }
    }


