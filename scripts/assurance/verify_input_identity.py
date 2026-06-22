#!/usr/bin/env python3
import argparse
from lib import load, assert_identity
p=argparse.ArgumentParser(); p.add_argument('--input',required=True); p.add_argument('--release-reference',required=True); p.add_argument('--git-commit',required=True); p.add_argument('--image-digest',required=True); a=p.parse_args()
try: assert_identity(load(a.input),a.release_reference,a.git_commit,a.image_digest)
except Exception as e: raise SystemExit(str(e))
print('input release identity PASS')
