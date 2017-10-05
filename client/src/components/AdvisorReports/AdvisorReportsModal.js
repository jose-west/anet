import React, { Component } from 'react'
import SimpleModal from 'components/SimpleModal'
import AdvisorReportsTable from 'components/AdvisorReports/AdvisorReportsTable'

import API from 'api'
const advisorsQueryUrl = `/api/reports/insights/advisors` // ?weeksAgo=3 default set at 3 weeks ago


class AdvisorReportsModal extends Component {
    constructor(props) {
        super(props)
        this.state = {
            advisors: []
        }
        this.handleModalOpen = this.handleModalOpen.bind(this)
    }

    handleModalOpen() {
        this.fetchAdvisors(this.props.id)
    }

    fetchAdvisors(orgId) {
        let query = `${advisorsQueryUrl}?orgId=${orgId}`
        let advisorsQuery = API.fetch(query)
        Promise.resolve(advisorsQuery).then(value => {
            this.setState({
                advisors: value
            })
        })
    }

    render() {
        return (
            <SimpleModal title={ this.props.name } onClickModalOpen={ this.handleModalOpen }>
                <AdvisorReportsTable
                    data={ this.state.advisors }
                    columnGroups={ this.props.columnGroups}
                    size="large" />
            </SimpleModal >
        )
    }
}

export default AdvisorReportsModal
