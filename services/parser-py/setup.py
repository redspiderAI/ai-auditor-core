from setuptools import setup, find_packages

setup(
    name="parser-py",
    version="0.1.0",
    packages=find_packages(),
    install_requires=[
        "python-docx==0.8.11",
        "PyPDF2==3.0.1",
        "grpcio==1.60.0",
        "grpcio-tools==1.60.0",
        "lxml==4.9.3",
    ],
    entry_points={
        "console_scripts": [
            "parser_py=src.main:main",
        ],
    },
)
