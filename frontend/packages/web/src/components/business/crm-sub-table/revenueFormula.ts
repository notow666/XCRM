type TokenType = 'number' | 'variable' | 'operator' | 'leftParenthesis' | 'rightParenthesis';

interface Token {
  type: TokenType;
  text: string;
}

const variableNames = ['放款金额', '回款金额', '成本金额', '杂费金额', '返佣金额'];
const precedence: Record<string, number> = {
  '+': 1,
  '-': 1,
  '*': 2,
  '/': 2,
  'u-': 3,
};

export function normalizeRevenueFormula(formula: string): string {
  return formula
    .replaceAll('（', '(')
    .replaceAll('）', ')')
    .replaceAll('＋', '+')
    .replaceAll('－', '-')
    .replaceAll('＊', '*')
    .replaceAll('／', '/');
}

function matchVariable(formula: string, index: number) {
  return variableNames.find((name) => formula.startsWith(name, index));
}

function tokenize(formula: string): Token[] {
  const tokens: Token[] = [];
  let index = 0;
  let previousType: TokenType | undefined;

  while (index < formula.length) {
    const current = formula[index];
    if (/\s/.test(current)) {
      index += 1;
    } else if (/\d|\./.test(current)) {
      const start = index;
      let dotCount = 0;
      while (index < formula.length && /\d|\./.test(formula[index])) {
        if (formula[index] === '.') {
          dotCount += 1;
        }
        index += 1;
      }
      const numberText = formula.slice(start, index);
      if (dotCount > 1 || numberText === '.' || !Number.isFinite(Number(numberText))) {
        throw new Error('invalid number');
      }
      tokens.push({ type: 'number', text: numberText });
      previousType = 'number';
    } else {
      const variable = matchVariable(formula, index);
      if (variable) {
        tokens.push({ type: 'variable', text: variable });
        previousType = 'variable';
        index += variable.length;
      } else if (current === '(') {
        tokens.push({ type: 'leftParenthesis', text: current });
        previousType = 'leftParenthesis';
        index += 1;
      } else if (current === ')') {
        tokens.push({ type: 'rightParenthesis', text: current });
        previousType = 'rightParenthesis';
        index += 1;
      } else if (['+', '-', '*', '/'].includes(current)) {
        const unary =
          current === '-' && (!previousType || previousType === 'operator' || previousType === 'leftParenthesis');
        if (!unary && (!previousType || previousType === 'operator' || previousType === 'leftParenthesis')) {
          throw new Error('invalid operator');
        }
        tokens.push({ type: 'operator', text: unary ? 'u-' : current });
        previousType = 'operator';
        index += 1;
      } else {
        throw new Error('invalid character');
      }
    }
  }

  if (!tokens.length || previousType === 'operator' || previousType === 'leftParenthesis') {
    throw new Error('incomplete formula');
  }
  return tokens;
}

function toReversePolish(tokens: Token[]): Token[] {
  const output: Token[] = [];
  const operators: Token[] = [];
  let previousType: TokenType | undefined;

  tokens.forEach((token) => {
    if (
      ['number', 'variable'].includes(token.type) &&
      previousType &&
      ['number', 'variable', 'rightParenthesis'].includes(previousType)
    ) {
      throw new Error('missing operator');
    }
    if (
      token.type === 'leftParenthesis' &&
      previousType &&
      ['number', 'variable', 'rightParenthesis'].includes(previousType)
    ) {
      throw new Error('missing multiplication operator');
    }

    if (token.type === 'number' || token.type === 'variable') {
      output.push(token);
    } else if (token.type === 'operator') {
      while (operators.length && operators.at(-1)?.type === 'operator') {
        const top = operators.at(-1) as Token;
        const rightAssociative = token.text === 'u-';
        const shouldPop = rightAssociative
          ? precedence[token.text] < precedence[top.text]
          : precedence[token.text] <= precedence[top.text];
        if (!shouldPop) {
          break;
        }
        output.push(operators.pop() as Token);
      }
      operators.push(token);
    } else if (token.type === 'leftParenthesis') {
      operators.push(token);
    } else {
      let foundLeftParenthesis = false;
      while (operators.length) {
        const operator = operators.pop() as Token;
        if (operator.type === 'leftParenthesis') {
          foundLeftParenthesis = true;
          break;
        }
        output.push(operator);
      }
      if (!foundLeftParenthesis) {
        throw new Error('unmatched parenthesis');
      }
    }
    previousType = token.type;
  });

  while (operators.length) {
    const operator = operators.pop() as Token;
    if (operator.type === 'leftParenthesis') {
      throw new Error('unmatched parenthesis');
    }
    output.push(operator);
  }
  return output;
}

function calculateReversePolish(tokens: Token[], variables: Record<string, number>): number {
  const values: number[] = [];
  tokens.forEach((token) => {
    if (token.type === 'number') {
      values.push(Number(token.text));
      return;
    }
    if (token.type === 'variable') {
      const value = variables[token.text];
      if (!Number.isFinite(value)) {
        throw new Error('missing variable');
      }
      values.push(value);
      return;
    }
    if (token.text === 'u-') {
      if (!values.length) {
        throw new Error('missing operand');
      }
      values.push(-(values.pop() as number));
      return;
    }
    if (values.length < 2) {
      throw new Error('missing operand');
    }
    const right = values.pop() as number;
    const left = values.pop() as number;
    if (token.text === '/' && right === 0) {
      throw new Error('division by zero');
    }
    let result: number;
    switch (token.text) {
      case '+':
        result = left + right;
        break;
      case '-':
        result = left - right;
        break;
      case '*':
        result = left * right;
        break;
      case '/':
        result = left / right;
        break;
      default:
        throw new Error('invalid operator');
    }
    if (!Number.isFinite(result)) {
      throw new Error('invalid result');
    }
    values.push(result);
  });
  if (values.length !== 1) {
    throw new Error('invalid formula');
  }
  return Math.round((values[0] + Number.EPSILON) * 100) / 100;
}

export default function evaluateRevenueFormula(formula: string, variables: Record<string, number>): number {
  return calculateReversePolish(toReversePolish(tokenize(normalizeRevenueFormula(formula).trim())), variables);
}
