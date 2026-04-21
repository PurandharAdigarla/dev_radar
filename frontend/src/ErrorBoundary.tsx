import { Component, type ReactNode } from "react";

interface State { hasError: boolean }
interface Props { children: ReactNode }

export class ErrorBoundary extends Component<Props, State> {
  state: State = { hasError: false };

  static getDerivedStateFromError(): State {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error("Uncaught error:", error);
  }

  render() {
    if (this.state.hasError) {
      return (
        <div style={{ padding: 32, fontFamily: "Inter, sans-serif" }}>
          <h1>Something broke.</h1>
          <p>Try refreshing the page.</p>
        </div>
      );
    }
    return this.props.children;
  }
}
