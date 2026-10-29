import { useEffect, useState } from "react";
import { errorMessage, useMockApi } from "./serviceMode";
/** Explicit loading/error lifecycle; API failures never preserve demo values. */
export function useResource<T>(load: (signal?: AbortSignal) => Promise<T>, initial: T, dependencies: unknown[] = []) {
  const [data, setData] = useState(initial), [error, setError] = useState(""), [loading, setLoading] = useState(!useMockApi);
  useEffect(() => { let live = true; if (useMockApi) return;
    const controller = new AbortController();
    setLoading(true); setError(""); setData(initial);
    Promise.resolve().then(() => { controller.signal.throwIfAborted(); return load(controller.signal); }).then(value => { if (live) setData(value); }).catch(e => { if (live && !controller.signal.aborted) setError(errorMessage(e)); }).finally(() => { if (live) setLoading(false); });
    return () => { live = false; controller.abort(); };
  }, dependencies);
  return { data, setData, error, setError, loading };
}
