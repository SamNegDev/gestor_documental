# Gestoría Portal Frontend

Nuevo frontend React para la migración progresiva desde Thymeleaf.

## Stack

- React
- TypeScript
- Vite
- React Router
- TanStack Query
- Lucide React

## Comandos

```powershell
npm install
npm run dev
npm run build
```

El servidor de desarrollo arranca en:

```text
http://localhost:5173
```

## Arquitectura inicial

```text
src/
  app/
    router.tsx
    shell/
  features/
    expedientes/
      data/
      pages/
      types.ts
  shared/
    api/
    ui/
```

## Estrategia de migración

1. Mantener Spring Boot como backend.
2. Crear endpoints JSON bajo `/api`.
3. Migrar pantallas una a una desde Thymeleaf.
4. Empezar por `/expedientes/:id` como pantalla piloto.
5. Extraer componentes compartidos a medida que se repitan patrones reales.

## Notas

- Durante la fase inicial se usan datos mock en `features/expedientes/data`.
- Vite proxya `/api` hacia `http://localhost:8080`.
- La autenticación actual de Spring Security requerirá una decisión específica para SPA.
