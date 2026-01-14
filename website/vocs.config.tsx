import { defineConfig } from 'vocs'

export default defineConfig({
  head: () => (
    <>
      <link rel="stylesheet" href="/styles.css" />
    </>
  ),
  title: 'Brane',
  titleTemplate: '%s – Brane',
  description: 'Modern, type-safe Java SDK for Ethereum',
  iconUrl: '/favicon.svg',
  logoUrl: {
    light: '/brane-logo-dark-nowords.svg',
    dark: '/brane-logo-dark-nowords.svg',
  },
  socials: [
    {
      icon: 'github',
      link: 'https://github.com/noise-xyz/brane',
    },
  ],
  topNav: [
    { text: 'Docs', link: '/quickstart', match: '/quickstart' },
    { text: 'GitHub', link: 'https://github.com/noise-xyz/brane' },
  ],
  sidebar: [
    {
      text: 'Getting Started',
      items: [
        { text: 'Quickstart', link: '/quickstart' },
        { text: 'Architecture', link: '/architecture' },
        { text: 'Performance', link: '/performance' },
      ],
    },
    {
      text: 'Brane.Reader',
      items: [
        { text: 'Reader API', link: '/reader/api' },
        { text: 'Call Simulation', link: '/reader/simulate' },
        { text: 'Subscriptions', link: '/reader/subscriptions' },
      ],
    },
    {
      text: 'Brane.Signer',
      items: [
        { text: 'Signer API', link: '/signer/api' },
        { text: 'Signers', link: '/signer/signers' },
        { text: 'HD Wallets', link: '/signer/hd-wallets' },
        { text: 'EIP-712 Typed Data', link: '/signer/eip712' },
        { text: 'Blob Transactions', link: '/signer/blobs' },
        { text: 'Custom Signers', link: '/signer/custom-signers' },
      ],
    },
    {
      text: 'Smart Contracts',
      items: [
        { text: 'Contract Interaction', link: '/contracts/interaction' },
        { text: 'Contract Bindings', link: '/contracts/bindings' },
        { text: 'Multicall', link: '/contracts/multicall' },
      ],
    },
    {
      text: 'Providers',
      items: [
        { text: 'HTTP Provider', link: '/providers/http' },
        { text: 'WebSocket Provider', link: '/providers/websocket' },
      ],
    },
    {
      text: 'Chains',
      items: [
        { text: 'Chain Profiles', link: '/chains/profiles' },
      ],
    },
    {
      text: 'Utilities',
      items: [
        { text: 'Type-Safe Primitives', link: '/utilities/types' },
        { text: 'ABI Encoding', link: '/utilities/abi' },
        { text: 'Error Handling', link: '/utilities/errors' },
        { text: 'Threading Model', link: '/utilities/threading' },
        { text: 'Metrics & Observability', link: '/utilities/metrics' },
      ],
    },
    {
      text: 'Testing',
      items: [
        { text: 'Overview', link: '/testing/overview' },
        { text: 'Setup', link: '/testing/setup' },
        { text: 'Test Accounts', link: '/testing/accounts' },
        { text: 'Impersonation', link: '/testing/impersonation' },
        { text: 'State Management', link: '/testing/state' },
        { text: 'Mining & Time Control', link: '/testing/mining-time' },
      ],
    },
  ],
  theme: {
    colorScheme: 'dark',
    accentColor: {
      light: '#f34533',
      dark: '#f34533',
    },
  },
  sponsors: [
    {
      name: 'Collaborator',
      height: 80,
      items: [
        [
          {
            name: 'Noise',
            link: 'https://noise.xyz',
            image: '/brand/noise-logo.svg',
          },
          {
            name: 'Become a Collaborator',
            link: 'https://tally.so/r/eq5XYO',
            image: '',
          },
        ],
      ],
    },
  ],
})
