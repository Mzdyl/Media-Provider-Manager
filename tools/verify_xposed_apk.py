#!/usr/bin/env python3
"""Verify a shipped APK's modern entry point and compile-only API boundary (including after R8)."""
import argparse
import struct
import zipfile


def uint(data, offset):
    return struct.unpack_from('<I', data, offset)[0]


def uleb(data, offset):
    value = 0
    shift = 0
    while True:
        byte = data[offset]
        offset += 1
        value |= (byte & 0x7f) << shift
        if byte < 128:
            return value, offset
        shift += 7


def dex_classes(data):
    if not data.startswith(b'dex\n'):
        raise ValueError('Unexpected DEX header')
    strings = []
    for index in range(uint(data, 0x38)):
        offset = uint(data, uint(data, 0x3c) + index * 4)
        _, offset = uleb(data, offset)
        strings.append(data[offset:data.index(b'\0', offset)])
    if any(b'Lde/robv/android/xposed/' in value for value in strings):
        raise ValueError('APK still references legacy Xposed classes')
    types = [strings[uint(data, uint(data, 0x44) + index * 4)] for index in range(uint(data, 0x40))]
    classes = {}
    for index in range(uint(data, 0x60)):
        definition = uint(data, 0x64) + index * 32
        descriptor = types[uint(data, definition)].decode('ascii')
        if descriptor.startswith('Lio/github/libxposed/api/'):
            raise ValueError(f'Framework-supplied API bundled into APK: {descriptor}')
        public_constructor = False
        offset = uint(data, definition + 24)
        if offset:
            counts = []
            for _ in range(4):
                count, offset = uleb(data, offset)
                counts.append(count)
            for _ in range(counts[0] + counts[1]):
                _, offset = uleb(data, offset)
                _, offset = uleb(data, offset)
            method_index = 0
            for _ in range(counts[2]):
                delta, offset = uleb(data, offset)
                access, offset = uleb(data, offset)
                code, offset = uleb(data, offset)
                method_index += delta
                method = uint(data, 0x5c) + method_index * 8
                proto = struct.unpack_from('<H', data, method + 2)[0]
                parameters = uint(data, uint(data, 0x4c) + proto * 12 + 8)
                if strings[uint(data, method + 4)] == b'<init>' and (not parameters or uint(data, parameters) == 0):
                    public_constructor = bool(access & 1 and code)
        classes[descriptor] = bool(uint(data, definition + 4) & 1) and public_constructor
    return classes


def verify(apk):
    with zipfile.ZipFile(apk) as archive:
        assert archive.testzip() is None, 'Corrupt APK entry'
        assert 'assets/xposed_init' not in archive.namelist(), 'Legacy entry is still packaged'
        base = 'META-INF/xposed/'
        properties = dict(line.split('=', 1) for line in archive.read(base + 'module.prop').decode().splitlines() if line and not line.startswith('#'))
        assert properties['minApiVersion'] == '102'
        assert properties['targetApiVersion'] == '102'
        entries = archive.read(base + 'java_init.list').decode().splitlines()
        assert len(entries) == 1 and entries[0], 'Expected one modern entry'
        scope = set(archive.read(base + 'scope.list').decode().splitlines())
        assert scope and '' not in scope
        assert 'me.gm.cleaner.plugin' not in scope, 'The app must use the framework service, not a self-hook'
        classes = {}
        for name in archive.namelist():
            if name.startswith('classes') and name.endswith('.dex'):
                classes.update(dex_classes(archive.read(name)))
        for name in entries + ['me.gm.cleaner.plugin.dao.MediaProviderRecordDatabase_Impl', 'io.github.libxposed.service.XposedProvider']:
            descriptor = 'L' + name.replace('.', '/') + ';'
            assert classes.get(descriptor), f'Missing public class or no-arg constructor: {name}'
        print(f'{apk}: API 102 entry, service provider, Room constructor and API isolation verified')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('apk')
    verify(parser.parse_args().apk)
