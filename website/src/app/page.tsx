import Image from "next/image";

export default function Home() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-zinc-50 font-sans dark:bg-black">
      <main className="flex w-full max-w-3xl flex-col items-center justify-center gap-12 px-8 py-24 text-center">
        <div className="space-y-4">
          <div className="relative inline-flex items-center justify-center">
            <h1 className="text-5xl font-bold tracking-tight text-black dark:text-white sm:text-6xl">
              Brane
            </h1>
            <div className="absolute left-full ml-3 inline-flex items-center whitespace-nowrap rounded-full border border-zinc-200 bg-zinc-100 px-2 py-0.5 text-xs font-medium text-zinc-800 dark:border-zinc-800 dark:bg-zinc-900 dark:text-zinc-300">
              v0.3.0
            </div>
          </div>
          <p className="text-2xl font-semibold text-black dark:text-white sm:text-3xl">
            The Modern Ethereum SDK for Java
          </p>
          <p className="text-xl text-zinc-600 dark:text-zinc-400">
            Type-safe, lightweight, and built for correctness. <br className="hidden sm:inline" />
            Inspired by the ergonomics of <span className="font-semibold text-black dark:text-white">viem</span> and <span className="font-semibold text-black dark:text-white">alloy</span>.
          </p>
        </div>

        <div className="flex w-full max-w-lg flex-row gap-3">
          <a
            href="https://docs.brane.sh"
            className="flex h-12 w-[25%] items-center justify-center rounded-full bg-black px-0 font-medium text-white transition-colors hover:bg-zinc-800 dark:bg-white dark:text-black dark:hover:bg-zinc-200"
          >
            Docs
          </a>
          <a
            href="https://tally.so/r/eq5XYO"
            className="flex h-12 flex-1 items-center justify-center rounded-full border border-zinc-200 bg-white px-6 font-medium text-black transition-colors hover:bg-zinc-50 dark:border-zinc-800 dark:bg-zinc-900 dark:text-white dark:hover:bg-zinc-800"
          >
            Become a Contributor
          </a>
        </div>
      </main>
    </div>
  );
}
