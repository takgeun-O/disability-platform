import fs from 'node:fs';
import ts from 'typescript';

const modules = new Map();
function moduleUrl(url) {
  if (modules.has(url.href)) return modules.get(url.href);
  const source = fs.readFileSync(url, 'utf8');
  let { outputText } = ts.transpileModule(source, {
    compilerOptions: { target: ts.ScriptTarget.ES2022, module: ts.ModuleKind.ESNext },
  });
  // 테스트 대상의 로컬 의존성도 같은 방식으로 로딩한다. 새 테스트 프레임워크는 필요 없다.
  outputText = outputText.replace(/from (['"])(\.[^'"]+)\1/g, (_match, _quote, relative) => {
    const dependency = new URL(relative.endsWith('.ts') ? relative : relative + '.ts', url);
    return `from '${moduleUrl(dependency)}'`;
  });
  const result = `data:text/javascript;base64,${Buffer.from(outputText).toString('base64')}`;
  modules.set(url.href, result);
  return result;
}
export function loadTypeScript(relative, base) { return import(moduleUrl(new URL(relative, base))); }
