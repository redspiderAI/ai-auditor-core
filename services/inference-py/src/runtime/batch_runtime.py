import logging
from pathlib import Path

from src.batch_runner import run_batch
from src.runtime.common import DEFAULT_INPUT_ROOT, DEFAULT_OUTPUT_ROOT


def run_batch_mode(parser_addr: str, input_root: Path | None, output_root: Path | None) -> None:
    input_dir = Path(input_root) if input_root else DEFAULT_INPUT_ROOT
    output_dir = Path(output_root) if output_root else DEFAULT_OUTPUT_ROOT
    logging.info(
        "Running batch mode: parser=%s input=%s output=%s",
        parser_addr,
        input_dir,
        output_dir,
    )
    run_batch(parser_addr=parser_addr, input_root=input_dir, output_root=output_dir)


__all__ = ["run_batch_mode"]
