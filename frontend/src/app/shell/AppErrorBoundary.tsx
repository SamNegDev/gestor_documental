import { Component, type ErrorInfo, type ReactNode } from "react";
import { AppErrorPage } from "./AppErrorPage";

type Props = {
  children: ReactNode;
};

type State = {
  error: Error | null;
};

export class AppErrorBoundary extends Component<Props, State> {
  state: State = { error: null };

  static getDerivedStateFromError(error: Error): State {
    return { error };
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error("Unexpected application render error", error, info);
  }

  render() {
    if (this.state.error) {
      return <AppErrorPage kind="unexpected" message="La vista no se pudo inicializar. Recarga para volver a cargar la sesion y los datos." />;
    }

    return this.props.children;
  }
}
