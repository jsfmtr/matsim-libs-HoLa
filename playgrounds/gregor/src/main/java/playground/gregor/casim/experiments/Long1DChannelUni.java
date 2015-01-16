/* *********************************************************************** *
 * project: org.matsim.*
 * CASimTest.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2014 by the members listed in the COPYING,        *
 *                   LICENSE and WARRANTY file.                            *
 * email           : info at matsim dot org                                *
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 *   This program is free software; you can redistribute it and/or modify  *
 *   it under the terms of the GNU General Public License as published by  *
 *   the Free Software Foundation; either version 2 of the License, or     *
 *   (at your option) any later version.                                   *
 *   See also COPYING, LICENSE and WARRANTY file                           *
 *                                                                         *
 * *********************************************************************** */

package playground.gregor.casim.experiments;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.Scenario;
import org.matsim.api.core.v01.network.Link;
import org.matsim.api.core.v01.network.Network;
import org.matsim.api.core.v01.network.NetworkFactory;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.api.experimental.events.EventsManager;
import org.matsim.core.config.Config;
import org.matsim.core.config.ConfigUtils;
import org.matsim.core.events.EventsManagerImpl;
import org.matsim.core.gbl.MatsimRandom;
import org.matsim.core.network.NetworkImpl;
import org.matsim.core.scenario.ScenarioUtils;
import org.matsim.core.utils.geometry.CoordImpl;

import playground.gregor.casim.events.CASimAgentConstructEvent;
import playground.gregor.casim.monitoring.CALinkMonitorExact;
import playground.gregor.casim.monitoring.CALinkMonitorExactIIUni;
import playground.gregor.casim.simulation.physics.AbstractCANetwork;
import playground.gregor.casim.simulation.physics.CAEvent;
import playground.gregor.casim.simulation.physics.CAEvent.CAEventType;
import playground.gregor.casim.simulation.physics.CAMoveableEntity;
import playground.gregor.casim.simulation.physics.CAMultiLaneNetworkFactory;
import playground.gregor.casim.simulation.physics.CANetwork;
import playground.gregor.casim.simulation.physics.CANetworkFactory;
import playground.gregor.casim.simulation.physics.CASimpleDynamicAgent;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPA;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPAFactory;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPAII;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPH;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPHFactory;
import playground.gregor.casim.simulation.physics.CASingleLaneDensityEstimatorSPHII;
import playground.gregor.casim.simulation.physics.CASingleLaneLink;
import playground.gregor.casim.simulation.physics.CASingleLaneNetworkFactory;
import playground.gregor.sim2d_v4.debugger.eventsbaseddebugger.EventBasedVisDebuggerEngine;
import playground.gregor.sim2d_v4.debugger.eventsbaseddebugger.InfoBox;
import playground.gregor.sim2d_v4.debugger.eventsbaseddebugger.QSimDensityDrawer;
import playground.gregor.sim2d_v4.scenario.Sim2DConfig;
import playground.gregor.sim2d_v4.scenario.Sim2DConfigUtils;
import playground.gregor.sim2d_v4.scenario.Sim2DScenario;
import playground.gregor.sim2d_v4.scenario.Sim2DScenarioUtils;

public class Long1DChannelUni {

	public final static boolean USE_MULTI_LANE_MODEL = false;
	private static BufferedWriter bw;
	private static boolean USE_SPH;

	public static final class Setting {
		public Setting(double bl, double bCor, double bEx) {
			this.bL = bl;
			this.bCor = bCor;
			this.bEx = bEx;
		}

		public double bL;
		public double bCor;
		public double bEx;
	}

	public static void main(String[] args) throws IOException {
		AbstractCANetwork.EMIT_VIS_EVENTS = false;
		USE_SPH = false;
		for (int R = 4; R <= 4; R++) {
			CASingleLaneDensityEstimatorSPH.H = R;
			CASingleLaneDensityEstimatorSPA.RANGE = R;
			CASingleLaneDensityEstimatorSPHII.H = R;
			CASingleLaneDensityEstimatorSPAII.RANGE = R;
			try {
				bw = new BufferedWriter(new FileWriter(new File(
						"/Users/laemmel/devel/bipedca/ant/tail2_gen_rho_new_uni_spa_"
								+ R)));
				// bw = new BufferedWriter(
				// new FileWriter(new File(
				// "/home/laemmel/scenarios/bipedca/rho_new_uni_spl_"
				// + R)));
			} catch (IOException e) {
				e.printStackTrace();
			}

			for (int ii = 0; ii < 20; ii++) {
				double w = 0;
				while (w < 1 || w > 3) {
					w = 2 + MatsimRandom.getRandom().nextGaussian();
				}
				Config c = ConfigUtils.createConfig();
				c.global().setCoordinateSystem("EPSG:3395");
				Scenario sc = ScenarioUtils.createScenario(c);

				// VIS only
				Sim2DConfig conf2d = Sim2DConfigUtils.createConfig();
				Sim2DScenario sc2d = Sim2DScenarioUtils
						.createSim2dScenario(conf2d);
				sc.addScenarioElement(Sim2DScenario.ELEMENT_NAME, sc2d);

				Network net = sc.getNetwork();
				((NetworkImpl) net).setCapacityPeriod(1);
				NetworkFactory fac = net.getFactory();

				Node n0 = fac.createNode(Id.createNodeId("0"), new CoordImpl(0,
						0));
				Node n1 = fac.createNode(Id.createNodeId("1"), new CoordImpl(
						100, 0));
				Node n2 = fac.createNode(Id.createNodeId("2"), new CoordImpl(
						200, 0));
				net.addNode(n2);
				net.addNode(n1);
				net.addNode(n0);

				Link l0 = fac.createLink(Id.createLinkId("0"), n0, n1);
				Link l0rev = fac.createLink(Id.createLinkId("0rev"), n1, n0);
				Link l1 = fac.createLink(Id.createLinkId("1"), n1, n2);
				Link l1rev = fac.createLink(Id.createLinkId("1rev"), n2, n1);

				l0.setLength(100);
				l1.setLength(20);

				l0rev.setLength(100);
				l1rev.setLength(20);
				net.addLink(l1);
				net.addLink(l0);
				net.addLink(l1rev);
				net.addLink(l0rev);

				l0.setCapacity(w);
				l1.setCapacity(w);
				l0rev.setCapacity(w);
				l1rev.setCapacity(w);

				List<Link> linksLR = new ArrayList<Link>();
				linksLR.add(l0);
				linksLR.add(l1);
				for (int i = 0; i < 1; i++) {
					for (double th = .925; th <= 1; th += 0.15) {
						runIt(net, linksLR, sc, th);
					}
					// for (double th = .26; th <= .4; th += 0.01) {
					// runIt(net, linksLR, sc, th);
					// }
					// for (double th = .45; th < .5; th += 0.05) {
					// runIt(net, linksLR, sc, th);
					// }
					// for (double th = .5; th <= 1.; th += 0.1) {
					// runIt(net, linksLR, sc, th);
					// }
				}
			}
			bw.close();
		}
	}

	private static void runIt(Network net, List<Link> linksLR, Scenario sc,
			double th) {
		// visualization stuff
		EventsManager em = new EventsManagerImpl();
		// // // if (iter == 9)

		if (AbstractCANetwork.EMIT_VIS_EVENTS) {
			EventBasedVisDebuggerEngine vis = new EventBasedVisDebuggerEngine(
					sc);
			em.addHandler(vis);
			QSimDensityDrawer qDbg = new QSimDensityDrawer(sc);
			em.addHandler(qDbg);
			vis.addAdditionalDrawer(new InfoBox(vis, sc));
			vis.addAdditionalDrawer(qDbg);
		}
		CANetworkFactory fac;
		if (USE_MULTI_LANE_MODEL) {
			fac = new CAMultiLaneNetworkFactory();
			fac.setDensityEstimatorFactory(null);

		} else {
			fac = new CASingleLaneNetworkFactory();
			if (USE_SPH) {
				fac.setDensityEstimatorFactory(new CASingleLaneDensityEstimatorSPHFactory());
			} else {
				fac.setDensityEstimatorFactory(new CASingleLaneDensityEstimatorSPAFactory());
			}

		}

		CANetwork caNet = fac.createCANetwork(net, em, null);

		int agents = 0;

		CASingleLaneLink caLink = (CASingleLaneLink) caNet.getCALink(linksLR
				.get(0).getId());
		CAMoveableEntity[] particles = caLink.getParticles();
		System.out.println("part left:" + particles.length);
		double oldR = 1;
		int tenth = particles.length / 10;
		for (int i = 0; i < particles.length - 1; i++) {
			double r = MatsimRandom.getRandom().nextDouble();
			if (r > th) {
				continue;
			}
			// agents++;
			CAMoveableEntity a = new CASimpleDynamicAgent(linksLR, 1,
					Id.create(agents++, CASimpleDynamicAgent.class), caLink);
			a.materialize(i, 1);
			particles[i] = a;
			CASimAgentConstructEvent ee = new CASimAgentConstructEvent(0, a);
			em.processEvent(ee);

			CAEvent e = new CAEvent(
					1 / (AbstractCANetwork.V_HAT * AbstractCANetwork.RHO_HAT),
					a, caLink, CAEventType.TTA);
			caNet.pushEvent(e);
			caNet.registerAgent(a);
		}

		CALinkMonitorExact monitor = new CALinkMonitorExactIIUni(
				caNet.getCALink(Id.createLinkId("0")), 10.,
				((CASingleLaneLink) caNet.getCALink(Id.createLinkId("0")))
						.getParticles(), caNet.getCALink(Id.createLinkId("0"))
						.getLink().getCapacity());
		caNet.addMonitor(monitor);
		monitor.init();
		caNet.run(3600);
		try {
			monitor.report(bw);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}