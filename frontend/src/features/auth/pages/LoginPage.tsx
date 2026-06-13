import { useMemo, useState } from "react";
import type { FormEvent } from "react";
import { AlertCircle, ArrowRight, CheckCircle2, Loader2, LockKeyhole, Mail } from "lucide-react";
import { login } from "../../../shared/api/authApi";
import { ApiError } from "../../../shared/api/http";
import "../../expedientes/styles/expedienteDetail.css";

function useLoginMessage() {
  return useMemo(() => {
    const params = new URLSearchParams(window.location.search);
    if (params.has("logout")) return { tone: "success", text: "Sesión cerrada correctamente." };
    if (params.has("expired")) return { tone: "warning", text: "La sesión ha caducado. Inicia sesión de nuevo." };
    if (params.has("denied")) return { tone: "warning", text: "Inicia sesión con un usuario autorizado." };
    if (params.has("error")) return { tone: "danger", text: "Email o contraseña incorrectos." };
    return null;
  }, []);
}

export function LoginPage() {
  const initialMessage = useLoginMessage();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [pending, setPending] = useState(false);
  const [message, setMessage] = useState(initialMessage);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPending(true);
    setMessage(null);
    try {
      const target = await login(username.trim(), password);
      window.location.href = target || "/admin/dashboard";
    } catch (cause) {
      const text = cause instanceof ApiError && cause.details ? cause.details : "No se pudo iniciar sesión.";
      setMessage({ tone: "danger", text });
      setPending(false);
    }
  }

  return (
    <main className="login-screen">
      <section className="login-panel" aria-labelledby="login-title">
        <div className="login-panel__brand">
          <img className="login-panel__logo" src="/assets/logos/casado-negrin-logo.png" alt="Casado Negrín Gestoría" />
        </div>

        <div className="login-panel__heading">
          <p className="eyebrow">Portal privado</p>
          <h1 id="login-title">Accede a tus expedientes</h1>
          <p>Gestión interna y portal cliente en una única entrada segura.</p>
        </div>

        {message ? (
          <div className={`login-message login-message--${message.tone}`} role="status">
            {message.tone === "success" ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
            <span>{message.text}</span>
          </div>
        ) : null}

        <form className="login-form" onSubmit={submit}>
          <label>
            <span>Email</span>
            <div className="login-input">
              <Mail size={18} />
              <input
                autoComplete="username"
                autoFocus
                disabled={pending}
                name="username"
                onChange={(event) => setUsername(event.target.value)}
                required
                type="email"
                value={username}
              />
            </div>
          </label>

          <label>
            <span>Contraseña</span>
            <div className="login-input">
              <LockKeyhole size={18} />
              <input
                autoComplete="current-password"
                disabled={pending}
                name="password"
                onChange={(event) => setPassword(event.target.value)}
                required
                type="password"
                value={password}
              />
            </div>
          </label>

          <button className="primary-button login-submit" disabled={pending} type="submit">
            {pending ? <Loader2 className="exp-detail-state__spinner" size={17} /> : <ArrowRight size={17} />}
            Entrar
          </button>
        </form>
      </section>
    </main>
  );
}
