import sys
import io
import os
import re
import json
import traceback
import builtins
import zipfile
import tarfile
import urllib.request
import urllib.error
import importlib
import importlib.util
import shutil

installed_in_session = set()

def get_ssl_context():
    import ssl
    try:
        return ssl.create_default_context()
    except Exception:
        return ssl._create_unverified_context()

def clean_package_name(pkg_str):
    s = pkg_str.strip()
    if s.startswith("!") or s.startswith("%"):
        s = s[1:].strip()
    if s.lower().startswith("pip install"):
        s = s[11:].strip()
    elif s.lower().startswith("pip3 install"):
        s = s[12:].strip()
    elif s.lower().startswith("pip"):
        s = s[3:].strip()

    tokens = s.split()
    pkgs = [t for t in tokens if not t.startswith("-")]
    if not pkgs:
        return "", ""

    target_pkg = pkgs[0]
    match = re.split(r'(==|>=|<=|>|<|~=)', target_pkg)
    if len(match) > 1:
        return match[0].strip(), match[1] + match[2]
    return target_pkg.strip(), ""

def is_package_installed(pkg_name):
    normalized = pkg_name.lower().replace("-", "_")
    if normalized in installed_in_session:
        return True
    try:
        spec = importlib.util.find_spec(normalized)
        return spec is not None
    except Exception:
        return False

def parse_requires_dist(requires_dist_list):
    deps = []
    if not requires_dist_list:
        return deps

    for req in requires_dist_list:
        if ";" in req:
            condition = req.split(";")[1]
            if "extra" in condition:
                continue

        dep_name = req.split(";")[0].strip()
        dep_name = re.split(r'[\s\(\)<>=!~]', dep_name)[0].strip()
        if dep_name and dep_name.lower() not in ["python", "pip", "setuptools", "wheel"]:
            deps.append(dep_name)
    return deps

def extract_sdist(content, target_dir):
    try:
        with tarfile.open(fileobj=io.BytesIO(content), mode="r:*") as tf:
            tmp_dir = os.path.join(target_dir, "_tmp_sdist")
            if os.path.exists(tmp_dir):
                shutil.rmtree(tmp_dir)
            tf.extractall(path=tmp_dir)

            found_pkg = False
            for root, dirs, files in os.walk(tmp_dir):
                if "__init__.py" in files:
                    pkg_name = os.path.basename(root)
                    dest = os.path.join(target_dir, pkg_name)
                    if os.path.exists(dest):
                        shutil.rmtree(dest)
                    shutil.move(root, dest)
                    found_pkg = True

            if os.path.exists(tmp_dir):
                shutil.rmtree(tmp_dir)
            return found_pkg
    except Exception:
        return False

def download_and_extract_pypi(pkg_name, target_dir, output_stream):
    clean_name, spec = clean_package_name(pkg_name)
    if not clean_name:
        output_stream.write("Invalid package name provided.\n")
        return False

    if is_package_installed(clean_name):
        output_stream.write(f"Requirement already satisfied: {clean_name}\n")
        return True

    output_stream.write(f"Collecting {clean_name}...\n")
    pypi_url = f"https://pypi.org/pypi/{clean_name}/json"

    req = urllib.request.Request(pypi_url, headers={'User-Agent': 'Vexor-Android-Pip/1.0'})
    ctx = get_ssl_context()

    try:
        with urllib.request.urlopen(req, context=ctx, timeout=15) as resp:
            data = json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        if e.code == 404:
            output_stream.write(f"Error: Could not find package '{clean_name}' on PyPI.\n")
        else:
            output_stream.write(f"HTTP Error {e.code} fetching package '{clean_name}'.\n")
        return False
    except Exception as e:
        output_stream.write(f"Error connecting to PyPI for '{clean_name}': {e}\n")
        return False

    urls = data.get("urls", [])
    if not urls:
        output_stream.write(f"No release files found for '{clean_name}'.\n")
        return False

    target_url = None
    target_filename = None
    is_wheel = False

    # 1. Look for pure-python wheels (none-any tag)
    for item in urls:
        fn = item.get("filename", "")
        ptype = item.get("packagetype", "")
        if ptype == "bdist_wheel" and ("none-any.whl" in fn or "abi3-none-any" in fn):
            target_url = item.get("url")
            target_filename = fn
            is_wheel = True
            break

    # 2. Look for source distribution (sdist)
    if not target_url:
        for item in urls:
            fn = item.get("filename", "")
            ptype = item.get("packagetype", "")
            if ptype == "sdist" or fn.endswith(".tar.gz") or fn.endswith(".zip") or fn.endswith(".tgz"):
                target_url = item.get("url")
                target_filename = fn
                is_wheel = False
                break

    if not target_url:
        output_stream.write(f"Package '{clean_name}' requires native C/C++ compiled binaries which cannot be installed dynamically at runtime on Android.\n")
        return False

    output_stream.write(f"Downloading {target_filename}...\n")
    try:
        dl_req = urllib.request.Request(target_url, headers={'User-Agent': 'Vexor-Android-Pip/1.0'})
        with urllib.request.urlopen(dl_req, context=ctx, timeout=30) as resp:
            content = resp.read()
    except Exception as e:
        output_stream.write(f"Error downloading '{target_filename}': {e}\n")
        return False

    if not os.path.exists(target_dir):
        os.makedirs(target_dir)

    output_stream.write(f"Extracting {clean_name}...\n")
    success = False
    try:
        if is_wheel or target_filename.endswith(".whl") or target_filename.endswith(".zip"):
            with zipfile.ZipFile(io.BytesIO(content)) as zf:
                zf.extractall(target_dir)
            success = True
        elif target_filename.endswith(".tar.gz") or target_filename.endswith(".tgz"):
            success = extract_sdist(content, target_dir)
            if not success:
                with tarfile.open(fileobj=io.BytesIO(content), mode="r:*") as tf:
                    tf.extractall(path=target_dir)
                success = True
    except Exception as e:
        output_stream.write(f"Error extracting '{clean_name}': {e}\n")
        return False

    if success:
        installed_in_session.add(clean_name.lower().replace("-", "_"))
        if target_dir not in sys.path:
            sys.path.insert(0, target_dir)
        importlib.invalidate_caches()

        requires_dist = data.get("info", {}).get("requires_dist")
        deps = parse_requires_dist(requires_dist)
        for dep in deps:
            if not is_package_installed(dep):
                output_stream.write(f"Installing dependency: {dep}\n")
                download_and_extract_pypi(dep, target_dir, output_stream)

        output_stream.write(f"Successfully installed {clean_name}\n")
        return True
    return False

def pip_install(package_name, target_dir, output_stream):
    raw_pkgs = package_name.strip().split()
    clean_pkgs = []
    for p in raw_pkgs:
        if p.lower() in ["pip", "pip3", "install"] or p.startswith("-"):
            continue
        clean_pkgs.append(p)

    if not clean_pkgs and package_name.strip():
        clean_pkgs = [package_name.strip()]

    overall_success = True
    for pkg in clean_pkgs:
        ok = download_and_extract_pypi(pkg, target_dir, output_stream)
        if not ok:
            overall_success = False

    return overall_success

class CallbackStream:
    def __init__(self, callback):
        self.callback = callback

    def write(self, text):
        if text:
            self.callback.onOutput(str(text))

    def flush(self):
        pass

def execute_interactive(code, input_callback, output_callback, extra_path=None):
    old_stdout = sys.stdout
    old_stderr = sys.stderr
    old_input = builtins.input

    site_packages = None
    if extra_path:
        site_packages = os.path.join(extra_path, "site-packages")
        if not os.path.exists(site_packages):
            os.makedirs(site_packages)
        if site_packages not in sys.path:
            sys.path.insert(0, site_packages)
        if extra_path not in sys.path:
            sys.path.insert(0, extra_path)

    stream = CallbackStream(output_callback)

    def custom_input(prompt=""):
        if prompt:
            stream.write(str(prompt))
        user_val = str(input_callback.readInput())
        stream.write(user_val + "\n")
        return user_val

    def user_install(package):
        target = site_packages if site_packages else os.getcwd()
        stream.write(f"Installing {package} via pip...\n")
        ok = pip_install(package, target, stream)
        if ok:
            stream.write(f"Successfully installed '{package}'!\n")
        else:
            stream.write(f"Failed to install '{package}'.\n")

    sys.stdout = stream
    sys.stderr = stream
    builtins.input = custom_input

    processed_lines = []
    for line in code.splitlines():
        stripped = line.strip()
        if (stripped.startswith("pip install") or
            stripped.startswith("!pip install") or
            stripped.startswith("%pip install") or
            stripped.startswith("pip3 install")):

            cmd_parts = stripped.lstrip("!%").split()
            pkg_parts = [p for p in cmd_parts if p.lower() not in ["pip", "pip3", "install"] and not p.startswith("-")]
            if pkg_parts:
                pkg_cmd = " ".join(pkg_parts)
                user_install(pkg_cmd)
            processed_lines.append(f"# {line}")
        else:
            processed_lines.append(line)

    executable_code = "\n".join(processed_lines)

    try:
        namespace = {
            "__name__": "__main__",
            "__doc__": None,
            "install": user_install,
            "pip_install": user_install,
        }
        exec(executable_code, namespace)
        stream.write("\n[Process completed]\n")
    except Exception as e:
        stream.write("\n" + traceback.format_exc() + "\n")
    finally:
        sys.stdout = old_stdout
        sys.stderr = old_stderr
        builtins.input = old_input

def install_lib_interactive(package_name, output_callback, extra_path=None):
    stream = CallbackStream(output_callback)
    site_packages = os.path.join(extra_path, "site-packages") if extra_path else os.getcwd()
    if not os.path.exists(site_packages):
        os.makedirs(site_packages)
    if site_packages not in sys.path:
        sys.path.insert(0, site_packages)
    if extra_path and extra_path not in sys.path:
        sys.path.insert(0, extra_path)

    stream.write(f"Installing {package_name}...\n")
    ok = pip_install(package_name, site_packages, stream)
    if ok:
        stream.write(f"\nSuccessfully installed '{package_name}'!\n")
    else:
        stream.write(f"\nFailed to install '{package_name}'.\n")
