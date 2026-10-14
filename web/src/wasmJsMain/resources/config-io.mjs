// SPDX-License-Identifier: AGPL-3.0-only
// Configuration text is opaque here: Kotlin preserves unsigned 64-bit color values.
export function writeConfiguration(storage, prefix, values) {
  const allowed = ['themeSettings', 'uiSettings', 'originalTitle', 'retainedSettings'];
  if (Object.keys(values).some(k => !allowed.includes(k)) || Object.values(values).some(v => typeof v !== 'string' || v.length > 2097152)) throw new Error('无效的设置写入');
  const previous = Object.fromEntries(Object.keys(values).map(k => [k, storage.getItem(prefix + k)]));
  try { for (const [key, value] of Object.entries(values)) storage.setItem(prefix + key, value); }
  catch (error) {
    for (const [key, value] of Object.entries(previous)) {
      if (value === null) storage.removeItem(prefix + key); else storage.setItem(prefix + key, value);
    }
    throw error;
  }
}
export async function configAction(action, p, storage, prefix, applyMedia) {
  if (action === 'configReadClipboard') {
    const raw = await navigator.clipboard.readText();
    if (!raw.trim()) throw new Error('剪贴板为空或浏览器未提供内容，请手动粘贴到配置框');
    return {raw};
  }
  if (action === 'configPick') {
    return new Promise((resolve, reject) => {
      const input = document.createElement('input'); input.type = 'file'; input.accept = '.json,application/json';
      input.oncancel = () => reject(new Error('已取消文件选择'));
      input.onchange = async () => {
        try { const file = input.files?.[0]; if (!file || file.size > 2097152) throw new Error('请选择不超过 2 MiB 的 JSON 文件'); resolve({raw: await file.text()}); }
        catch (e) { reject(e); }
      };
      input.click();
    });
  }
  if (action === 'configCopy' || action === 'configExport') {
    if (typeof p.content !== 'string' || p.content.length > 2097152) throw new Error('配置为空或超过 2 MiB');
    if (action === 'configCopy') await navigator.clipboard.writeText(p.content);
    else {
      const url = URL.createObjectURL(new Blob([p.content], {type:'application/json'}));
      const a = document.createElement('a'); a.href = url; a.download = p.kind === 'sources' ? 'animeko-sources.json' : 'animeko-settings.json'; a.click();
      setTimeout(() => URL.revokeObjectURL(url), 60000);
    }
    return {};
  }
  if (action === 'configApply') {
    const previous = Object.fromEntries(Object.keys(p.values).map(k => [k, storage.getItem(prefix + k)]));
    writeConfiguration(storage, prefix, p.values);
    try { if (p.mediaSettings !== undefined) await applyMedia(p.mediaSettings); }
    catch (error) {
      for (const [key,value] of Object.entries(previous)) {
        if (value === null) storage.removeItem(prefix + key); else storage.setItem(prefix + key,value);
      }
      throw error;
    }
    return {};
  }
  throw new Error('未知配置操作');
}
