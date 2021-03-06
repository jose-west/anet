import PropTypes from 'prop-types'
import React, { Component } from 'react'
import { mapDispatchToProps, propTypes as pagePropTypes } from 'components/Page'
import {Nav as BSNav, NavItem, NavDropdown, MenuItem} from 'react-bootstrap'
import {IndexLinkContainer as Link, LinkContainer} from 'react-router-bootstrap'
import Settings from 'Settings'
import pluralize from 'pluralize'

import {Organization, Person} from 'models'
import {INSIGHTS, INSIGHT_DETAILS} from 'pages/insights/Show'

import AppContext from 'components/AppContext'
import { ResponsiveLayoutContext } from 'components/ResponsiveLayout'
import { withRouter } from 'react-router-dom'
import { connect } from 'react-redux'

import {ScrollLink, scrollSpy} from 'react-scroll'

export const AnchorNavItem = (props) => {
	const {to, ...remainingProps} = props
	const ScrollLinkNavItem = ScrollLink(NavItem)
	return (
		<ResponsiveLayoutContext.Consumer>
			{context =>
				<ScrollLinkNavItem
					activeClass="active"
					to={to}
					spy={true}
					hashSpy={true}
					smooth={true}
					duration={500}
					containerId="main-viewport"
					onClick={() => context.showFloatingMenu(false)}
					//TODO: fix the need for offset
					offset={-context.topbarOffset}
					{...remainingProps}
				>
					{props.children}
				</ScrollLinkNavItem>
			}
		</ResponsiveLayoutContext.Consumer>
	)
}

class BaseNav extends Component {
	static propTypes = {
		...pagePropTypes,
		currentUser: PropTypes.instanceOf(Person),
		appSettings: PropTypes.object,
		organizations: PropTypes.array,
	}

	componentDidMount() {
		scrollSpy.update()
	}

	render() {
		const { currentUser } = this.props
		const { organizations } = this.props || []
		const { appSettings } = this.props || {}
		const externalDocumentationUrl = appSettings.EXTERNAL_DOCUMENTATION_LINK_URL
		const externalDocumentationUrlText = appSettings.EXTERNAL_DOCUMENTATION_LINK_TEXT

		const path = this.props.location.pathname
		const inAdmin = path.indexOf('/admin') === 0
		const inOrg = path.indexOf('/organizations') === 0
		const inInsights = path.indexOf('/insights') === 0

		const myOrg = currentUser.position ? currentUser.position.organization : null
		let orgUuid, myOrgUuid
		if (inOrg) {
			orgUuid = path.split('/')[2]
			myOrgUuid = myOrg && myOrg.uuid
		}

		return (
			<BSNav bsStyle="pills" stacked id="leftNav" className="hide-for-print">
				<Link to="/" onClick={this.props.clearSearchQuery}>
					<NavItem>Home</NavItem>
				</Link>

				<BSNav id="search-nav" />

				{currentUser.uuid && <Link to={{pathname: '/reports/mine'}} onClick={this.props.clearSearchQuery}>
					<NavItem>My reports</NavItem>
				</Link>}

				<BSNav id="reports-nav" />

				{myOrg && <Link to={Organization.pathFor(myOrg)} onClick={this.props.clearSearchQuery}>
					<NavItem id="my-organization">My organization <br /><small>{myOrg.shortName}</small></NavItem>
				</Link>}

				<BSNav id="myorg-nav" />

				<NavDropdown title={Settings.fields.advisor.org.allOrgName} id="advisor-organizations" active={inOrg && orgUuid !== myOrgUuid}>
					{Organization.map(organizations, org =>
						<Link to={Organization.pathFor(org)} key={org.uuid} onClick={this.props.clearSearchQuery}>
							<MenuItem>{org.shortName}</MenuItem>
						</Link>
					)}
				</NavDropdown>

				<BSNav id="org-nav" />

				<Link to="/rollup" onClick={this.props.clearSearchQuery}>
					<NavItem>Daily rollup</NavItem>
				</Link>

				{process.env.NODE_ENV === 'development' &&
					<Link to="/graphiql" onClick={this.props.clearSearchQuery}>
						<NavItem>GraphQL</NavItem>
					</Link>
				}

				{currentUser.isAdmin() &&
					<LinkContainer to="/admin" onClick={this.props.clearSearchQuery}>
						<NavItem>Admin</NavItem>
					</LinkContainer>
				}

				{inAdmin &&
					<BSNav>
						<LinkContainer to={"/admin/mergePeople"} onClick={this.props.clearSearchQuery}><NavItem>Merge people</NavItem></LinkContainer>
						<LinkContainer to={"/admin/authorizationGroups"} onClick={this.props.clearSearchQuery}><NavItem>Authorization groups</NavItem></LinkContainer>
					</BSNav>
				}

				{externalDocumentationUrl && externalDocumentationUrlText &&
					<NavItem href={externalDocumentationUrl} target="_extdocs">{externalDocumentationUrlText}</NavItem>
				}

				<Link to="/help" onClick={this.props.clearSearchQuery}>
					<NavItem>Help</NavItem>
				</Link>

				{(currentUser.isAdmin() || currentUser.isSuperUser()) &&
					<NavDropdown title="Insights" id="insights" active={inInsights}>
						{INSIGHTS.map(insight =>
							<Link to={"/insights/" + insight} key={insight} onClick={this.props.clearSearchQuery}>
								<MenuItem>{INSIGHT_DETAILS[insight].navTitle}</MenuItem>
							</Link>)
						}
					</NavDropdown>
				}
			</BSNav>
		)
	}
}

const mapStateToProps = (state, ownProps) => ({
	searchQuery: state.searchQuery
})

const Nav = (props) => (
	<AppContext.Consumer>
		{context =>
			<BaseNav appSettings={context.appSettings} currentUser={context.currentUser} {...props} />
		}
	</AppContext.Consumer>
)

export default connect(mapStateToProps, mapDispatchToProps, null, { pure: false })(withRouter(Nav))
