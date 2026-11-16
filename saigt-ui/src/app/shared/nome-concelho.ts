const PARTICULAS = new Set(['de', 'da', 'do', 'das', 'dos', 'e', 'o', 'a']);

/**
 * A API devolve a designação do SNIT tal como vem («VILA NOVA DE GAIA»). As partículas ficam em minúsculas fora da
 * primeira palavra, também entre hífenes. A exportação da API usa a mesma regra (`NomesConcelho`): ao mudar aqui,
 * muda-se lá.
 */
export function formatarNomeConcelho(nome: string | null | undefined): string {
  let primeira = true;
  return (nome ?? '')
    .trim()
    .toLocaleLowerCase('pt-PT')
    .replace(/\p{L}+/gu, (palavra) => {
      const formatada = !primeira && PARTICULAS.has(palavra) ? palavra : palavra.charAt(0).toLocaleUpperCase('pt-PT') + palavra.slice(1);
      primeira = false;
      return formatada;
    });
}
