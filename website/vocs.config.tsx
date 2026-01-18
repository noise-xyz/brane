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
    { text: 'Docs', link: '/docs/quickstart', match: '/docs' },
    { text: 'GitHub', link: 'https://github.com/noise-xyz/brane' },
  ],
  sidebar: {
    '/docs/': [
      {
        text: 'Getting Started',
        items: [
          { text: 'Quickstart', link: '/docs/quickstart' },
          { text: 'Architecture', link: '/docs/architecture' },
          { text: 'Performance', link: '/docs/performance' },
        ],
      },
      {
        text: 'Brane.Reader',
        items: [
          { text: 'Reader API', link: '/docs/reader/api' },
          { text: 'Call Simulation', link: '/docs/reader/simulate' },
          { text: 'Subscriptions', link: '/docs/reader/subscriptions' },
        ],
      },
      {
        text: 'Brane.Signer',
        items: [
          { text: 'Signer API', link: '/docs/signer/api' },
          { text: 'Signers', link: '/docs/signer/signers' },
          { text: 'HD Wallets', link: '/docs/signer/hd-wallets' },
          { text: 'EIP-712 Typed Data', link: '/docs/signer/eip712' },
          { text: 'Blob Transactions', link: '/docs/signer/blobs' },
          { text: 'Custom Signers', link: '/docs/signer/custom-signers' },
        ],
      },
      {
        text: 'Smart Contracts',
        items: [
          { text: 'Contract Interaction', link: '/docs/contracts/interaction' },
          { text: 'Contract Bindings', link: '/docs/contracts/bindings' },
          { text: 'Multicall', link: '/docs/contracts/multicall' },
        ],
      },
      {
        text: 'Providers',
        items: [
          { text: 'HTTP Provider', link: '/docs/providers/http' },
          { text: 'WebSocket Provider', link: '/docs/providers/websocket' },
        ],
      },
      {
        text: 'Chains',
        items: [
          { text: 'Chain Profiles', link: '/docs/chains/profiles' },
        ],
      },
      {
        text: 'Utilities',
        items: [
          { text: 'Type-Safe Primitives', link: '/docs/utilities/types' },
          { text: 'ABI Encoding', link: '/docs/utilities/abi' },
          { text: 'Error Handling', link: '/docs/utilities/errors' },
          { text: 'Threading Model', link: '/docs/utilities/threading' },
          { text: 'Metrics & Observability', link: '/docs/utilities/metrics' },
        ],
      },
      {
        text: 'Testing',
        items: [
          { text: 'Overview', link: '/docs/testing/overview' },
          { text: 'Setup', link: '/docs/testing/setup' },
          { text: 'Test Accounts', link: '/docs/testing/accounts' },
          { text: 'Impersonation', link: '/docs/testing/impersonation' },
          { text: 'State Management', link: '/docs/testing/state' },
          { text: 'Mining & Time Control', link: '/docs/testing/mining-time' },
        ],
      },
      {
        text: 'Javadoc',
        link: 'https://javadoc.io/doc/sh.brane/brane-core/latest/index.html',
      },
    ],
  },
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
