import { readFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { describe, expect, it } from 'vitest';

function blocoApi(): string {
  const conf = readFileSync(resolve(process.cwd(), 'nginx.conf'), 'utf8');
  const bloco = /location \/api\/ \{([\s\S]*?)\n\s*\}/.exec(conf);
  if (!bloco) {
    throw new Error('nginx.conf sem location /api/');
  }
  return bloco[1].replace(/#.*$/gm, '');
}

describe('nginx.conf, location /api/', () => {
  it('deixa passar um ficheiro de 500 MB (o maior limite por ficheiro dos itens) com o envelope multipart', () => {
    const limite = /client_max_body_size\s+(\d+)([km]?);/i.exec(blocoApi());
    expect(limite).not.toBeNull();
    const [, valor, unidade] = limite!;
    const megabytes = unidade.toLowerCase() === 'm' ? Number(valor) : unidade.toLowerCase() === 'k' ? Number(valor) / 1024 : Number(valor) / 1024 / 1024;
    expect(Number(valor) === 0 || megabytes > 500).toBe(true);
  });

  it('envia o upload à API em streaming, sem o guardar em disco', () => {
    expect(blocoApi()).toMatch(/proxy_request_buffering\s+off;/);
  });
});
