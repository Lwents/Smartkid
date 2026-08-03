import fs from "node:fs/promises";
import path from "node:path";

export class JsonStore<T> {
  private writeQueue: Promise<void> = Promise.resolve();

  constructor(
    private readonly filename: string,
    private readonly fallback: T,
  ) {}

  async read(): Promise<T> {
    try {
      return JSON.parse(await fs.readFile(this.filename, "utf8")) as T;
    } catch (error) {
      if ((error as NodeJS.ErrnoException).code !== "ENOENT") throw error;
      return structuredClone(this.fallback);
    }
  }

  async write(value: T): Promise<void> {
    this.writeQueue = this.writeQueue.then(async () => {
      await fs.mkdir(path.dirname(this.filename), { recursive: true });
      const temporary = `${this.filename}.${process.pid}.${Date.now()}.tmp`;
      await fs.writeFile(temporary, `${JSON.stringify(value, null, 2)}\n`, {
        encoding: "utf8",
        mode: 0o600,
      });
      await fs.rename(temporary, this.filename);
    });
    return this.writeQueue;
  }

  async update(updater: (current: T) => T | Promise<T>): Promise<T> {
    const current = await this.read();
    const updated = await updater(current);
    await this.write(updated);
    return updated;
  }
}
