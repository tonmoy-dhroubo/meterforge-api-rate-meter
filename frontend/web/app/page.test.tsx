import { render, screen } from "@testing-library/react";
import { describe, it, expect, vi } from "vitest";
import LoginPage from "./login/page";
import { Providers } from "./providers";

// Mock next/navigation
vi.mock("next/navigation", () => ({
  useRouter: () => ({
    push: vi.fn(),
  }),
  usePathname: () => "/login",
  useParams: () => ({}),
}));

describe("LoginPage", () => {
  it("renders MeterForge console login title and input fields", () => {
    render(
      <Providers>
        <LoginPage />
      </Providers>
    );

    expect(screen.getByText("Sign in to Console")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("staff@meterforge.local")).toBeInTheDocument();
    expect(screen.getByPlaceholderText("••••••••••••")).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /Sign in with Password/i })).toBeInTheDocument();
  });

  it("renders quick demo login buttons for Owner, Member, and Viewer", () => {
    render(
      <Providers>
        <LoginPage />
      </Providers>
    );

    expect(screen.getByText(/Owner \(Acme APIs\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Member \(Write Access\)/i)).toBeInTheDocument();
    expect(screen.getByText(/Viewer \(Read-Only\)/i)).toBeInTheDocument();
  });
});
