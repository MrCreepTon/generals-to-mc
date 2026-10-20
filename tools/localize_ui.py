"""Register new UI translations in ui_translations.json, then regenerate RU/EN resources."""
from pathlib import Path
import json,re
root=Path(__file__).resolve().parents[1]
translations=json.loads(Path(__file__).with_name('ui_translations.json').read_text(encoding='utf-8'))
def key(text):
    value=0
    for c in text:value=(31*value+ord(c))&0xffffffff
    return 'generalsmc.text.'+format(value,'x')
assert len({key(source) for source in translations})==len(translations),'UI translation key collision'
assert all(english.strip() for english in translations.values()),'Empty English translation'
for folder,wrapper in [('src/client/java/local/generalsmc/client','L.s'),('src/main/java/local/generalsmc/nativebridge','local.generalsmc.LocalText.token')]:
    for file in (root/folder).glob('*.java'):
        if 'Bench' in file.name or file.name=='L.java':continue
        source=file.read_text(encoding='utf-8')
        def replace(match):
            if not re.search('[А-Яа-яЁё]',match.group()):return match.group()
            value=json.loads(match.group());assert value in translations,('Missing English translation',str(file),value)
            before=source[max(0,match.start()-80):match.start()]
            if before.endswith(wrapper+'('):return match.group()
            return wrapper+'('+match.group()+')'
        source=re.sub(r'"(?:\\.|[^"\\])*"',replace,source)
        if folder.startswith('src/client'):source=source.replace('Component.literal(', 'L.component(')
        file.write_text(source,encoding='utf-8')
lang=root/'src/main/resources/assets/generalsmc/lang'
for code in ['en_us','ru_ru']:
    file=lang/(code+'.json');values=json.loads(file.read_text(encoding='utf-8'));values.pop('key.generalsmc.open',None)
    for source,english in translations.items():values[key(source)]=source if code=='ru_ru' else english
    file.write_text(json.dumps(values,ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
print('RU/EN translations:',len(translations))
