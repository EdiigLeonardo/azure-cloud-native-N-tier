<script setup lang="ts">
import { onMounted, ref } from 'vue'

type Item = {
    id?: number
    nome?: string
    preco?: number
}

const backendBaseUrl = (import.meta.env.VITE_BACKEND_URL || '').replace(/\/$/, '')
const apiBaseUrl = backendBaseUrl ? `${backendBaseUrl}/api` : '/api'
const item = ref<Item | null>(null)
const loading = ref(true)
const error = ref('')

async function fetchBackendItem() {
    loading.value = true
    error.value = ''

    try {
        const response = await fetch(`${apiBaseUrl}/items/1`, {
            method: 'GET',
            headers: {
                Accept: 'application/json',
            },
        })

        if (!response.ok) {
            if (response.status === 404) {
                const createResponse = await fetch(`${apiBaseUrl}/items`, {
                    method: 'POST',
                    headers: {
                        'Content-Type': 'application/json',
                        Accept: 'application/json',
                    },
                    body: JSON.stringify({
                        nome: 'Produto demo',
                        preco: 42.5,
                    }),
                })

                if (!createResponse.ok) {
                    throw new Error(`Não foi possível criar um item de teste: ${createResponse.status}`)
                }

                const location = createResponse.headers.get('Location') || '/api/items/1'
                const itemResponse = await fetch(`${backendBaseUrl ? `${backendBaseUrl}${location}` : location}`)

                if (!itemResponse.ok) {
                    throw new Error(`Erro ao obter o item criado: ${itemResponse.status}`)
                }

                item.value = await itemResponse.json()
                return
            }

            throw new Error(`Erro ao carregar o backend: ${response.status}`)
        }

        item.value = await response.json()
    } catch (err) {
        error.value = err instanceof Error ? err.message : 'Erro desconhecido ao conectar ao backend.'
        item.value = null
    } finally {
        loading.value = false
    }
}

onMounted(() => {
    fetchBackendItem()
})
</script>

<template>
    <section class="welcome">
        <h2>Frontend conectado ao backend</h2>

        <button type="button" @click="fetchBackendItem">Recarregar</button>

        <p v-if="loading">A carregar dados do backend...</p>

        <div v-else-if="error" class="error">
            {{ error }}
        </div>

        <div v-else-if="item" class="item-card">
            <p><strong>ID:</strong> {{ item.id }}</p>
            <p><strong>Nome:</strong> {{ item.nome }}</p>
            <p><strong>Preço:</strong> {{ item.preco }}€</p>
        </div>

        <p v-else>Nenhum item encontrado.</p>
    </section>
</template>

<style scoped>
.welcome {
    max-width: 420px;
    margin: 2rem auto;
    padding: 1.5rem;
    border: 1px solid #dfe3e8;
    border-radius: 12px;
    background: #fff;
    box-shadow: 0 8px 20px rgba(0, 0, 0, 0.04);
}

button {
    margin-bottom: 1rem;
    padding: 0.6rem 1rem;
    border: none;
    border-radius: 8px;
    background: #2563eb;
    color: white;
    cursor: pointer;
}

.item-card {
    background: #f8fafc;
    border-radius: 10px;
    padding: 1rem;
}

.error {
    color: #b91c1c;
    font-weight: 600;
}
</style>
