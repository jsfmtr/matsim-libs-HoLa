/* *********************************************************************** *
 * project: org.matsim.*
 * NewAgentPtPlan.java
 *                                                                         *
 * *********************************************************************** *
 *                                                                         *
 * copyright       : (C) 2007 by the members listed in the COPYING,        *
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

package playground.yu.newPlans;

import org.matsim.gbl.Gbl;
import org.matsim.interfaces.core.v01.Person;
import org.matsim.interfaces.core.v01.PersonAlgorithm;
import org.matsim.interfaces.core.v01.Plan;
import org.matsim.interfaces.core.v01.Population;
import org.matsim.network.MatsimNetworkReader;
import org.matsim.network.NetworkLayer;
import org.matsim.population.MatsimPopulationReader;
import org.matsim.population.PopulationImpl;
import org.matsim.population.PopulationReader;

/**
 * writes new Plansfile, in which every person will has 2 plans, one with type
 * "iv" and the other with type "oev", whose leg mode will be "pt" and who will
 * have only a blank <Route></Rout>
 * 
 * @author ychen
 * 
 */
public class SelectedPlans extends NewPopulation implements PersonAlgorithm {
	/**
	 * Constructor, writes file-head
	 * 
	 * @param plans -
	 *            a Plans Object, which derives from MATSim plansfile
	 */
	public SelectedPlans(Population plans) {
		super(plans);
	}

	@Override
	public void run(Person person) {
		Plan selectedPlan = person.getSelectedPlan();
		person.getPlans().clear();
		person.addPlan(selectedPlan);
		pw.writePerson(person);
	}
	public static void main(final String[] args) {
		final String netFilename = "../data/schweiz/input/ch.xml";
		final String plansFilename = "../data/schweiz/input/459.100.plans.xml.gz";
		Gbl.createConfig(new String[] { "../data/schweiz/selectedPlans.xml" });

		NetworkLayer network = new NetworkLayer();
		new MatsimNetworkReader(network).readFile(netFilename);

		Population population = new PopulationImpl();
		SelectedPlans sp = new SelectedPlans(population);
		PopulationReader plansReader = new MatsimPopulationReader(population,
				network);
		plansReader.readFile(plansFilename);
		sp.run(population);
		sp.writeEndPlans();
	}
}
