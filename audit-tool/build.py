"""Build audit.exe (console CLI) and audit-gui.exe (windowed GUI) with PyInstaller --onefile.

PyInstaller is installed only into audit-tool/.venv (pinned in requirements-build.txt), never into global Python.
Usage: python build.py   -> dist/audit.exe, dist/audit-gui.exe (then runs `audit.exe selftest`)
"""
import os, subprocess, sys, venv

HERE = os.path.dirname(os.path.abspath(__file__))
VENV = os.path.join(HERE, '.venv')
PY = os.path.join(VENV, 'Scripts', 'python.exe') if os.name == 'nt' else os.path.join(VENV, 'bin', 'python')
EXE = '.exe' if os.name == 'nt' else ''


def run(*cmd):
    print('>', ' '.join(cmd), flush=True)
    subprocess.run(cmd, check=True, cwd=HERE)


def main():
    if not os.path.isfile(PY):
        venv.create(VENV, with_pip=True)
    run(PY, '-m', 'pip', 'install', '--disable-pip-version-check', '-q', '-r', 'requirements-build.txt')
    common = ['--noconfirm', '--clean', '--onefile', '--paths', HERE,
              '--distpath', 'dist', '--workpath', 'build', '--specpath', 'build']
    run(PY, '-m', 'PyInstaller', *common, '--console', '--name', 'audit', '__main__.py')
    run(PY, '-m', 'PyInstaller', *common, '--windowed', '--name', 'audit-gui', 'gui.py')
    for n in ('audit', 'audit-gui'):
        p = os.path.join(HERE, 'dist', n + EXE)
        print('%s %.1f MB' % (p, os.path.getsize(p) / 1e6))
    run(os.path.join(HERE, 'dist', 'audit' + EXE), 'selftest')
    return 0


if __name__ == '__main__':
    sys.exit(main())
