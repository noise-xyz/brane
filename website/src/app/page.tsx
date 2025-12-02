import Image from "next/image";

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex w-full max-w-3xl flex-col items-center justify-center gap-12 px-8 py-24 text-center">
        <div className="space-y-4">
          <h1 className="text-5xl font-bold tracking-tight text-black dark:text-white sm:text-6xl">
            Brane
          </h1>
          <p className="text-xl text-zinc-600 dark:text-zinc-400">
            A lightweight, zero-dependency Java SDK for Ethereum.
          </p>
        </div>

        <div className="grid w-full max-w-lg grid-cols-1 gap-4 sm:grid-cols-2">
          <a
            href="https://docs.brane.sh"
            className="flex h-12 items-center justify-center rounded-full bg-blue-600 px-6 font-medium text-white transition-colors hover:bg-blue-700"
          >
            Documentation
          </a>
          <div
            className="flex h-12 cursor-not-allowed items-center justify-center rounded-full border border-zinc-200 bg-zinc-50 px-6 font-medium text-zinc-400 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-600"
          >
            GitHub (Coming Soon)
          </div>
        </div>
        <div className="flex gap-4 text-sm text-zinc-500">
          <a href="/javadoc/index.html" className="hover:underline">API Reference (Javadoc)</a>
        </div>

        <div className="mt-12 grid w-full grid-cols-1 gap-8 text-left sm:grid-cols-3">
          <div className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="mb-2 font-semibold text-black dark:text-white">Type Safe</h3>
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              Built with modern Java features like Records and Sealed Interfaces for maximum safety.
            </p>
          </div>
          <div className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="mb-2 font-semibold text-black dark:text-white">Zero Deps</h3>
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              No external dependencies. Just pure, high-performance Java code.
            </p>
          </div>
          <div className="rounded-2xl border border-zinc-200 bg-white p-6 dark:border-zinc-800 dark:bg-zinc-900">
            <h3 className="mb-2 font-semibold text-black dark:text-white">Native ABI</h3>
            <p className="text-sm text-zinc-600 dark:text-zinc-400">
              Custom-built ABI encoder and decoder optimized for speed and correctness.
            </p>
          </div>
        </div>
        <footer className="mt-16 flex items-center justify-center gap-2 text-sm text-zinc-500">
          <p>Deployed via GitHub Actions 🚀</p>
        </footer>
      </main>
    </div>
  );
}
