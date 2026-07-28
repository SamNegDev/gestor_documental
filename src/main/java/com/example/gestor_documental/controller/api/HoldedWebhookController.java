package com.example.gestor_documental.controller.api;
import com.example.gestor_documental.service.impl.HoldedFacturaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/webhooks/holded") @RequiredArgsConstructor
public class HoldedWebhookController {
 private final HoldedFacturaService service;
 @PostMapping public ResponseEntity<Void> receive(@RequestHeader(value="X-Holded-Event-Id",required=false) String eventId,@RequestHeader(value="X-Holded-Signature",required=false) String signature,@RequestBody byte[] payload){service.recibirWebhook(eventId,signature,payload);return ResponseEntity.noContent().build();}
}
