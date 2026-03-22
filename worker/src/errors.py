class WorkerError(Exception):
    """Vyvolána, když worker job selže se známou chybou."""

    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code
