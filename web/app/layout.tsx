import type { Metadata } from "next";
import { Manrope } from "next/font/google";
import { QueryProvider } from "@/components/providers/query-provider";
import "./globals.css";

const manrope = Manrope({ subsets: ["latin"], variable: "--font-manrope" });

export const metadata: Metadata = {
  title: "Gully CRM Executive Intelligence",
  description: "Hybrid executive intelligence analytics for Gully CRM"
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en" className={`${manrope.variable} dark`}>
      <body>
        <QueryProvider>{children}</QueryProvider>
      </body>
    </html>
  );
}
