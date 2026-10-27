/** @type {import('next').NextConfig} */
const nextConfig = {
  reactStrictMode: false,
  typescript: {
    ignoreBuildErrors: true,
  },
  eslint: {
    ignoreDuringBuilds: true,
  },
  async rewrites() {
    const apiTarget = process.env.NEXT_PUBLIC_API_GATEWAY_URL || process.env.NEXT_PUBLIC_API_URL || 'https://commerce-os-api.onrender.com';
    return [
      {
        source: '/api/:path*',
        destination: `${apiTarget.replace(/\/$/, '')}/api/:path*`,
      },
    ];
  },

};

module.exports = nextConfig;
