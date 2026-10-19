import type { NextConfig } from "next";

// Le backend Spring tourne sur localhost:8080 et n'a pas de configuration
// CORS. Plutôt que d'en ajouter côté Spring, on proxifie /api/* : le
// navigateur ne parle qu'à l'origine Next.js, qui relaie côté serveur.
const backendUrl = process.env.BACKEND_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [
      {
        source: "/api/:path*",
        destination: `${backendUrl}/api/:path*`,
      },
    ];
  },
};

export default nextConfig;
