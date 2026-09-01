import io
import sys
import traceback


class _CallbackWriter(io.TextIOBase):
    def __init__(self, callback, stream_name):
        self.callback = callback
        self.stream_name = stream_name

    def write(self, s):
        if s:
            self.callback.onOutput(s, self.stream_name)
        return len(s)

    def flush(self):
        pass


class _CallbackReader(io.TextIOBase):
    """Backs sys.stdin: blocks the (background) Python thread until the
    UI thread supplies a line via callback.onInputRequested()."""

    def __init__(self, callback):
        self.callback = callback

    def readline(self, limit=-1):
        line = self.callback.onInputRequested()
        return (line if line is not None else "") + "\n"

    def read(self, size=-1):
        return self.readline()


def run_code(code, callback):
    old_stdout, old_stderr, old_stdin = sys.stdout, sys.stderr, sys.stdin
    sys.stdout = _CallbackWriter(callback, "stdout")
    sys.stderr = _CallbackWriter(callback, "stderr")
    sys.stdin = _CallbackReader(callback)
    try:
        exec(compile(code, "<idepython>", "exec"), {"__name__": "__main__"})
    except BaseException:
        traceback.print_exc()
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        sys.stdin = old_stdin
        callback.onFinished()
