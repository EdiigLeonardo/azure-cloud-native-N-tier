function validateOrder(body) {
  const errors = [];
  if (!body || typeof body.customer !== 'string' || body.customer.trim() === '') {
    errors.push('customer é obrigatório');
  }
  if (!body || typeof body.amount !== 'number' || Number.isNaN(body.amount)) {
    errors.push('amount tem de ser um número');
  }
  return errors;
}

module.exports = { validateOrder };
