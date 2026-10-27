"""Builds the pairing-secret test vector used by PairingSecretTest.

The hash below is copied from androidtvremote2 (Apache-2.0),
src/androidtvremote2/pairing.py, PairingProtocol.async_finish_pairing.
Run: python3 tools/make_pairing_vector.py  (needs `pip install cryptography`)
"""
import datetime, hashlib, json, pathlib
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

OUT = pathlib.Path(__file__).resolve().parent.parent / "app/src/test/resources/pairing"


def make_cert(cn):
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, cn)])
    now = datetime.datetime(2026, 1, 1)
    cert = (x509.CertificateBuilder().subject_name(name).issuer_name(name)
            .public_key(key.public_key()).serial_number(1)
            .not_valid_before(now).not_valid_after(now + datetime.timedelta(days=3650))
            .sign(key, hashes.SHA256()))
    return cert


def reference_hash(client_cert, server_cert, pairing_code):
    # --- verbatim logic from androidtvremote2 pairing.py ---
    cn = client_cert.public_key().public_numbers()
    sn = server_cert.public_key().public_numbers()
    client_modulus, client_exponent = cn.n, cn.e
    server_modulus, server_exponent = sn.n, sn.e
    h = hashlib.sha256()
    h.update(bytes.fromhex(f"{client_modulus:X}"))
    h.update(bytes.fromhex(f"0{client_exponent:X}"))
    h.update(bytes.fromhex(f"{server_modulus:X}"))
    h.update(bytes.fromhex(f"0{server_exponent:X}"))
    h.update(bytes.fromhex(pairing_code[2:]))
    return h.digest()


if __name__ == "__main__":
    OUT.mkdir(parents=True, exist_ok=True)
    client, server = make_cert("client"), make_cert("server")
    for n, c in (("client", client), ("server", server)):
        (OUT / f"{n}.pem").write_bytes(c.public_bytes(serialization.Encoding.PEM))
    suffix = "1A2B"
    digest = reference_hash(client, server, "00" + suffix)
    code = f"{digest[0]:02X}{suffix}"
    assert reference_hash(client, server, code)[0] == int(code[:2], 16)
    (OUT / "vector.json").write_text(json.dumps({"code": code, "sha256": digest.hex()}, indent=2) + "\n")
    print(code, digest.hex())
