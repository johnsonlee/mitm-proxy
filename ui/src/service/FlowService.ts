export default class FlowService {
    getFlows(): Promise<{ path: string, host: string }[]> {
        return fetch('/api/flow')
            .then((res) => res.json())
            .then((json) => {
                return json.map((flow: any) => {
                    return { ...flow, url: `${flow.protocol}://${flow.host}${flow.path}` }
                })
            })
            .then((json) => json.sort((a: any, b: any) => a.id < b.id ? 1 : a.id > b.id ? -1 : 0))
    }

    getFlow(id: number | string) {
        return fetch(`/api/flow/${id}`)
            .then((res) => res.json())
    }
}